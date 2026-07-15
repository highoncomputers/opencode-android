package ai.opencode.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ai.opencode.core.session.Session

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "directory")
    val directory: String? = null,

    @ColumnInfo(name = "project_id")
    val projectId: String? = null,

    @ColumnInfo(name = "agent")
    val agent: String? = null,

    @ColumnInfo(name = "model_provider_id")
    val modelProviderId: String? = null,

    @ColumnInfo(name = "model_id")
    val modelId: String? = null,

    @ColumnInfo(name = "model_variant")
    val modelVariant: String? = null,

    @ColumnInfo(name = "cost")
    val cost: Double = 0.0,

    @ColumnInfo(name = "token_input")
    val tokenInput: Long = 0,

    @ColumnInfo(name = "token_output")
    val tokenOutput: Long = 0,

    @ColumnInfo(name = "token_cache")
    val tokenCache: Long = 0,

    @ColumnInfo(name = "parent_id")
    val parentId: String? = null,

    @ColumnInfo(name = "time_created")
    val timeCreated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "time_updated")
    val timeUpdated: Long = System.currentTimeMillis()
) {
    fun toInfo(): Session.Info = Session.Info(
        id = Session.ID(id),
        title = title,
        directory = directory,
        projectID = projectId,
        agent = agent,
        model = if (modelProviderId != null && modelId != null) {
            Session.ModelRef(
                providerID = modelProviderId,
                id = modelId,
                variant = modelVariant
            )
        } else null,
        cost = cost,
        tokens = Session.TokenUsage(
            input = tokenInput,
            output = tokenOutput,
            cache = tokenCache
        ),
        parentID = parentId?.let { Session.ID(it) },
        timeCreated = timeCreated,
        timeUpdated = timeUpdated
    )

    companion object {
        fun fromInfo(info: Session.Info): SessionEntity = SessionEntity(
            id = info.id.value,
            title = info.title,
            directory = info.directory,
            projectId = info.projectID,
            agent = info.agent,
            modelProviderId = info.model?.providerID,
            modelId = info.model?.id,
            modelVariant = info.model?.variant,
            cost = info.cost,
            tokenInput = info.tokens.input,
            tokenOutput = info.tokens.output,
            tokenCache = info.tokens.cache,
            parentId = info.parentID?.value,
            timeCreated = info.timeCreated,
            timeUpdated = info.timeUpdated
        )
    }
}
