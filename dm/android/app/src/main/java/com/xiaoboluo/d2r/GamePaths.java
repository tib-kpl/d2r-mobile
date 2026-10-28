// 本地游戏目录解析；不复制或修改玩家文件。
// Resolve local game directories without copying or modifying the player's files.
package com.xiaoboluo.d2r;

import java.io.File;
import java.io.IOException;

final class GamePaths {
    private GamePaths() {}

    static File treePath(File volume, String relative) throws IOException {
        if (relative.startsWith("/") || relative.indexOf('\0') >= 0) throw new IOException("Invalid document path");
        File root = volume.getCanonicalFile();
        File result = new File(root, relative).getCanonicalFile();
        if (!result.equals(root) && !result.toPath().startsWith(root.toPath())) throw new IOException("Document outside volume");
        return result;
    }

    static File dataDirectory(File chosen) {
        for (File candidate : new File[] {new File(chosen, "data"), new File(chosen, "RomFS/data"), chosen}) {
            File[] archives = candidate.listFiles(f -> f.isFile() && f.getName().endsWith(".data"));
            if (archives != null && archives.length > 0) return candidate;
        }
        return null;
    }

    static File runtimeDirectory(File chosen, File internal, File external) {
        File parent = chosen.getParentFile();
        for (File candidate : new File[] {new File(chosen, "android"), new File(chosen, "dm"), chosen,
                parent == null ? chosen : new File(parent, "android"), internal, external}) {
            if (candidate != null && new File(candidate, "out/main.patched.bin").canRead()) return candidate;
        }
        return null;
    }
}
