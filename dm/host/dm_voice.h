// 法语音频按导入包清单检查，必须所有对应档案可读才启用。
// Enable French audio only when every archive named by the import pack is readable.
#pragma once
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int dm_voice_fr_available(const char *romfs) {
    const char *derived = getenv("DM_DERIVED");
    if (!derived) return 0;
    FILE *f = fopen(derived, "r"); if (!f) return 0;
    char line[256], path[1400]; int found = 0, ok = 1;
    while (fgets(line, sizeof line, f)) {
        if (strncmp(line, "VOICE\t3\t", 8)) continue;
        char *name = line + 8; name[strcspn(name, "\r\n")] = 0;
        if (strlen(name) != 16 || name[2] != '-' || strcmp(name + 11, ".data")) { ok = 0; break; }
        for (int i = 0; i < 11; i++) if (i != 2 && (name[i] < '0' || name[i] > '9')) ok = 0;
        if (!ok) break;
        snprintf(path, sizeof path, "%s/data/%s", romfs, name);
        found++; if (access(path, R_OK)) { ok = 0; break; }
    }
    fclose(f); return found > 0 && ok;
}
