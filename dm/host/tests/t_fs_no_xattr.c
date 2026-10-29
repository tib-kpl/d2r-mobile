/* Android SD/FUSE regression: readable overlays need no extended attributes.
   Inject the filesystem's xattr error while using real directories and files. */
#include <sys/xattr.h>
#include <errno.h>
#include <assert.h>
#include <stdint.h>
#include <time.h>
/* Save transactions are outside this test; fail if they are invoked. */
int clonefile(const char *src, const char *dst, int flags) {
    (void)src; (void)dst; (void)flags; assert(0); return -1;
}
#define RENAME_SWAP 2u
static int renamex_np(const char *src, const char *dst, unsigned flags) {
    (void)src; (void)dst; (void)flags; assert(0); return -1;
}
static uint64_t clock_gettime_nsec_np(clockid_t clock) {
    struct timespec ts; assert(!clock_gettime(clock, &ts));
    return (uint64_t)ts.tv_sec * 1000000000ull + ts.tv_nsec;
}
static int xattr_error = EOPNOTSUPP;
static ssize_t no_get(const char *p, const char *key, void *v, size_t n) {
    (void)p; (void)key; (void)v; (void)n; errno = xattr_error; return -1;
}
static int no_set(const char *p, const char *key, const void *v, size_t n, int flags) {
    (void)p; (void)key; (void)v; (void)n; (void)flags; errno = xattr_error; return -1;
}
static int no_del(const char *p, const char *key) {
    (void)p; (void)key; errno = xattr_error; return -1;
}
#define lgetxattr no_get
#define lsetxattr no_set
#define lremovexattr no_del
#include "../hle_fs.c"
#undef lgetxattr
#undef lsetxattr
#undef lremovexattr
struct dm_flags g_flag;
uint64_t g_t0_ns;
uint64_t now_ns(void) { return 0; }
void dm_prof_add(int id, uint64_t ns, uint64_t b) { (void)id; (void)ns; (void)b; }
void dm_guest_write_prepare(void *a, size_t n) { (void)a; (void)n; }
int dm_custom_lang_explicit(void) { return -1; }
int dm_custom_voice_explicit(void) { return -1; }
static void fixture(const char *root, const char *name, const char *text) {
    char path[1024]; snprintf(path, sizeof path, "%s/%s", root, name);
    FILE *file = fopen(path, "wb"); assert(file);
    assert(fwrite(text, 1, strlen(text), file) == strlen(text)); assert(!fclose(file));
}
static void expect(const char *path, const char *text) {
    uint64_t h; assert(h_fs_OpenFile(&h, path, MODE_READ) == RES_OK);
    char data[32] = {0}; size_t size = 0;
    assert(h_fs_ReadFile(&size, h, 0, data, sizeof data) == RES_OK);
    assert(size == strlen(text) && !memcmp(data, text, size)); h_fs_CloseFile(h);
}
int main(void) {
    char root[] = "/tmp/dm-sd-xattr-XXXXXX"; assert(mkdtemp(root));
    char base[1024], overlay[1024], temp[1024];
    snprintf(base, sizeof base, "%s/base", root); assert(!mkdir(base, 0700));
    snprintf(overlay, sizeof overlay, "%s/overlay", root); assert(!mkdir(overlay, 0700));
    snprintf(temp, sizeof temp, "%s/temp", root); assert(!mkdir(temp, 0700));
    fixture(base, "config", "base"); fixture(base, "fallback", "original");
    fixture(overlay, "config", "overlay");
    setenv("DM_ROMFS", base, 1); setenv("DM_ROMFS_OVERLAY", overlay, 1);
    assert(h_fs_MountRom("rom", NULL, 0) == RES_OK);
    expect("rom:/config", "overlay"); expect("rom:/fallback", "original");
    uint64_t h;
    assert(h_fs_OpenFile(&h, "rom:/Config", MODE_READ) == RES_PATH_NOT_FOUND);
    assert(h_fs_OpenFile(&h, "rom:/missing", MODE_READ) == RES_PATH_NOT_FOUND);
    setenv("DM_TEMP", temp, 1); assert(h_fs_MountTemp("tmp") == RES_OK);
    assert(h_fs_CreateFile("tmp:/ordinary", 0) == RES_OK);
    assert(h_fs_OpenFile(&h, "tmp:/ordinary", MODE_WRITE) == RES_OK);
    assert(h_fs_WriteFile(h, 0, "saved", 5, NULL) == RES_OK); h_fs_CloseFile(h);
    expect("tmp:/ordinary", "saved");
    char path[1024]; snprintf(path, sizeof path, "%s/ordinary", temp);
    assert(set_guest_name(path, "tmp:/different-alias") == -1); // Aliases still require xattrs.
    xattr_error = EACCES;
    assert(h_fs_OpenFile(&h, "tmp:/ordinary", MODE_READ) != RES_OK); // Do not mask access errors.
    xattr_error = ENOATTR; expect("tmp:/ordinary", "saved");
    rm_tree(root);
    puts("PASS: SD overlay, fallback, exact names, writes and access-error handling");
}
