#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <signal.h>
#include <termios.h>
#include <poll.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "OpenCodePTY"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...) fprintf(stdout, __VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

typedef struct {
    int master_fd;
    int slave_fd;
    pid_t child_pid;
} PtyHandle;

static void set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags >= 0) {
        fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    }
}

JNIEXPORT jint JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativeCreate(
    JNIEnv *env, jobject thiz,
    jstring cmd, jstring cwd,
    jobjectArray args, jobjectArray envVars,
    jint rows, jint cols) {

    int master_fd = -1;
    int slave_fd = -1;
    pid_t child_pid;

    master_fd = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (master_fd < 0) {
        LOGE("Failed to open /dev/ptmx: %s", strerror(errno));
        return -1;
    }

    if (grantpt(master_fd) < 0 || unlockpt(master_fd) < 0) {
        LOGE("Failed to grant/unlock pt: %s", strerror(errno));
        close(master_fd);
        return -1;
    }

    char slave_name[256];
    if (ptsname_r(master_fd, slave_name, sizeof(slave_name)) != 0) {
        LOGE("Failed to get slave name: %s", strerror(errno));
        close(master_fd);
        return -1;
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short)(rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short)(cols > 0 ? cols : 80);
    ioctl(master_fd, TIOCSWINSZ, &ws);

    struct termios tios;
    memset(&tios, 0, sizeof(tios));
    tios.c_iflag = ICRNL | IXON;
    tios.c_oflag = OPOST | NL0 | CR0 | TAB0 | BS0 | VT0 | FF0;
    tios.c_cflag = CREAD | CS8 | HUPCL;
    tios.c_lflag = ICANON | ISIG | ECHO | ECHOE | ECHOK | ECHOKE | ECHOCTL | ECHOIP | IEXTEN;
    cfmakeraw(&tios);
    tios.c_cc[VMIN] = 1;
    tios.c_cc[VTIME] = 0;

    child_pid = fork();
    if (child_pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master_fd);
        return -1;
    }

    if (child_pid == 0) {
        close(master_fd);

        setsid();

        slave_fd = open(slave_name, O_RDWR | O_CLOEXEC);
        if (slave_fd < 0) {
            _exit(127);
        }

        ioctl(slave_fd, TIOCSCTTY, NULL);

        tcsetattr(slave_fd, TCSANOW, &tios);

        dup2(slave_fd, 0);
        dup2(slave_fd, 1);
        dup2(slave_fd, 2);
        if (slave_fd > 2) close(slave_fd);

        if (cwd != NULL) {
            const char *cwd_str = (*env)->GetStringUTFChars(env, cwd, NULL);
            if (cwd_str != NULL) {
                chdir(cwd_str);
                (*env)->ReleaseStringUTFChars(env, cwd, cwd_str);
            }
        }

        if (envVars != NULL) {
            int envLen = (*env)->GetArrayLength(env, envVars);
            for (int i = 0; i < envLen; i++) {
                jstring jenv = (jstring)(*env)->GetObjectArrayElement(env, envVars, i);
                if (jenv != NULL) {
                    const char *envStr = (*env)->GetStringUTFChars(env, jenv, NULL);
                    if (envStr != NULL) {
                        putenv((char *)envStr);
                        (*env)->ReleaseStringUTFChars(env, jenv, envStr);
                    }
                }
            }
        }

        const char *cmd_str = (*env)->GetStringUTFChars(env, cmd, NULL);
        if (cmd_str == NULL) _exit(127);

        int argc = 0;
        char **argv = NULL;
        if (args != NULL) {
            argc = (*env)->GetArrayLength(env, args) + 1;
            argv = (char **)malloc(sizeof(char *) * (argc + 1));
            argv[0] = (char *)cmd_str;
            for (int i = 1; i < argc; i++) {
                jstring jarg = (jstring)(*env)->GetObjectArrayElement(env, args, i - 1);
                if (jarg != NULL) {
                    argv[i] = (char *)(*env)->GetStringUTFChars(env, jarg, NULL);
                } else {
                    argv[i] = "";
                }
            }
            argv[argc] = NULL;
        } else {
            argc = 1;
            argv = (char **)malloc(sizeof(char *) * 2);
            argv[0] = (char *)cmd_str;
            argv[1] = NULL;
        }

        execvp(cmd_str, argv);

        LOGE("execvp failed: %s", strerror(errno));
        _exit(127);
    }

    (*env)->ReleaseStringUTFChars(env, cmd, (*env)->GetStringUTFChars(env, cmd, NULL));

    set_nonblocking(master_fd);

    PtyHandle *handle = (PtyHandle *)malloc(sizeof(PtyHandle));
    handle->master_fd = master_fd;
    handle->slave_fd = slave_fd;
    handle->child_pid = child_pid;

    return (jint)(intptr_t)handle;
}

JNIEXPORT jint JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativeRead(
    JNIEnv *env, jobject thiz,
    jint fd, jbyteArray buffer, jint offset, jint length) {

    char *buf = (char *)malloc(length);
    ssize_t n = read(fd, buf, length);

    if (n > 0) {
        (*env)->SetByteArrayRegion(env, buffer, offset, n, (jbyte *)buf);
    }
    free(buf);

    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativeWrite(
    JNIEnv *env, jobject thiz,
    jint fd, jbyteArray data, jint offset, jint length) {

    char *buf = (char *)malloc(length);
    (*env)->GetByteArrayRegion(env, data, offset, length, (jbyte *)buf);

    ssize_t written = write(fd, buf, length);
    free(buf);

    return (jint)written;
}

JNIEXPORT void JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativeSetSize(
    JNIEnv *env, jobject thiz,
    jint fd, jint cols, jint rows) {

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (unsigned short)cols;
    ws.ws_row = (unsigned short)rows;
    ioctl(fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativeWaitForExit(
    JNIEnv *env, jobject thiz,
    jint pid) {

    int status;
    waitpid(pid, &status, 0);

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    return -1;
}

JNIEXPORT void JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativeClose(
    JNIEnv *env, jobject thiz,
    jint fd) {

    close(fd);
}

JNIEXPORT void JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativeKill(
    JNIEnv *env, jobject thiz,
    jint pid) {

    kill(pid, SIGHUP);
    kill(pid, SIGTERM);
    usleep(100000);
    kill(pid, SIGKILL);
}

JNIEXPORT jint JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativeGetAvailable(
    JNIEnv *env, jobject thiz,
    jint fd) {

    int count = 0;
    if (ioctl(fd, FIONREAD, &count) < 0) {
        return -1;
    }
    return count;
}

JNIEXPORT jint JNICALL
Java_ai_opencode_platform_pty_NativePTY_nativePoll(
    JNIEnv *env, jobject thiz,
    jint fd, jint timeoutMs) {

    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLIN;
    pfd.revents = 0;

    int result = poll(&pfd, 1, timeoutMs);
    if (result < 0) return -1;
    if (result == 0) return 0;
    if (pfd.revents & POLLIN) return 1;
    if (pfd.revents & POLLHUP) return 2;
    return -1;
}
