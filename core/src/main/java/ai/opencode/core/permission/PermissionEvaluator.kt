package ai.opencode.core.permission

import ai.opencode.core.permission.Permission.Action
import ai.opencode.core.permission.Permission.Resolution
import ai.opencode.core.permission.Permission.Rule
import ai.opencode.core.permission.Permission.Ruleset
import ai.opencode.core.permission.Permission.Source
import ai.opencode.core.permission.Permission.SourceType
import java.util.regex.Pattern

object PermissionEvaluator {

    fun evaluate(
        rulesets: List<Ruleset>,
        action: Action,
        resource: String,
        toolName: String,
        context: Permission.Context
    ): Resolution {
        val candidates = mutableListOf<MatchedRule>()

        for (ruleset in rulesets) {
            for (rule in ruleset.rules) {
                if (!ruleApplies(rule, toolName)) continue
                val specificity = calculateSpecificity(rule.pattern, resource)
                if (specificity >= 0) {
                    candidates.add(
                        MatchedRule(
                            rule = rule,
                            ruleset = ruleset,
                            specificity = specificity
                        )
                    )
                }
            }
        }

        val remembered = context.rememberedChoices[buildRememberKey(resource, toolName)]
        if (remembered != null) {
            return Resolution(
                requestID = Permission.ID.create(),
                action = remembered,
                sources = listOf(
                    Source(
                        type = SourceType.SessionHistory,
                        value = "remembered_choice",
                        description = "Previously remembered choice for this resource"
                    )
                )
            )
        }

        val configMatch = matchAgainstConfigPatterns(resource, context)
        if (configMatch != null) {
            return Resolution(
                requestID = Permission.ID.create(),
                action = configMatch,
                sources = listOf(
                    Source(
                        type = SourceType.Config,
                        value = resource,
                        description = "Matched config allow/deny pattern"
                    )
                )
            )
        }

        if (candidates.isEmpty()) {
            return Resolution(
                requestID = Permission.ID.create(),
                action = Action.Prompt,
                sources = listOf(
                    Source(
                        type = SourceType.SystemDefault,
                        value = "no_match",
                        description = "No matching rules found"
                    )
                )
            )
        }

        val best = candidates.maxByOrNull { it.specificity }!!
        return Resolution(
            requestID = Permission.ID.create(),
            action = best.rule.action,
            matchedRule = best.rule,
            matchedRuleset = best.ruleset,
            sources = listOf(
                Source(
                    type = SourceType.Ruleset,
                    value = best.rule.pattern,
                    description = best.rule.description,
                    confidence = calculateConfidence(best.specificity)
                )
            )
        )
    }

    fun matchesPattern(pattern: String, input: String): Boolean {
        return globToRegex(pattern).matches(input)
    }

    fun calculateSpecificity(pattern: String, input: String): Int {
        if (!matchesPattern(pattern, input)) return -1
        val wildcardCount = pattern.count { it == '*' || it == '?' }
        val literalCount = pattern.length - wildcardCount
        val exactnessBonus = if (pattern == input) 1000 else 0
        return exactnessBonus + literalCount * 10 - wildcardCount * 5
    }

    fun globToRegex(pattern: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < pattern.length) {
            when (pattern[i]) {
                '*' -> {
                    if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                        sb.append(".*")
                        i += 2
                        if (i < pattern.length && pattern[i] == '/') {
                            sb.append("\\/?")
                            i++
                        }
                    } else {
                        sb.append("[^/]*")
                        i++
                    }
                }
                '?' -> {
                    sb.append("[^/]")
                    i++
                }
                '[' -> {
                    val end = pattern.indexOf(']', i)
                    if (end > i) {
                        sb.append(Pattern.quote(pattern.substring(i, end + 1)))
                        i = end + 1
                    } else {
                        sb.append("\\[")
                        i++
                    }
                }
                '.', '(', ')', '+', '^', '$', '|', '\\', '{', '}' -> {
                    sb.append("\\${pattern[i]}")
                    i++
                }
                else -> {
                    sb.append(Pattern.quote(pattern[i].toString()))
                    i++
                }
            }
        }
        sb.append("$")
        return Regex(sb.toString())
    }

    private fun ruleApplies(rule: Rule, toolName: String): Boolean {
        if (rule.tools != null && rule.tools.isNotEmpty()) {
            return rule.tools.any { tool ->
                tool.equals(toolName, ignoreCase = true) ||
                    matchesPattern(tool.lowercase(), toolName.lowercase())
            }
        }
        return true
    }

    private fun matchAgainstConfigPatterns(
        resource: String,
        context: Permission.Context
    ): Action? {
        for (pattern in context.deniedPatterns) {
            if (matchesPattern(pattern, resource)) return Action.Deny
        }
        for (pattern in context.allowedPatterns) {
            if (matchesPattern(pattern, resource)) return Action.Allow
        }
        return null
    }

    private fun buildRememberKey(resource: String, toolName: String): String {
        return "$toolName:$resource"
    }

    private fun calculateConfidence(specificity: Int): Double {
        return (specificity.coerceIn(0, 1000) / 1000.0).coerceIn(0.0, 1.0)
    }

    private data class MatchedRule(
        val rule: Rule,
        val ruleset: Ruleset,
        val specificity: Int
    )
}
