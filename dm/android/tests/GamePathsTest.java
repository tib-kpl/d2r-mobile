// 用临时文件验证目录发现与越界拒绝。 | Test path discovery and traversal rejection with temporary files.
package com.xiaoboluo.d2r;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class GamePathsTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("dm-paths-");
        try {
            Path game = Files.createDirectories(root.resolve("My Game"));
            Path data = Files.createDirectories(game.resolve("data"));
            Files.write(data.resolve("00-00000001.data"), new byte[] {1});
            Path runtime = Files.createDirectories(game.resolve("android/out"));
            Files.write(runtime.resolve("main.patched.bin"), new byte[] {1});
            check(GamePaths.dataDirectory(game.toFile()).equals(data.toFile()));
            check(GamePaths.dataDirectory(data.toFile()).equals(data.toFile()));
            check(GamePaths.dataDirectory(root.resolve("missing").toFile()) == null);
            check(GamePaths.runtimeDirectory(game.toFile(), null, null).equals(game.resolve("android").toFile()));
            check(GamePaths.runtimeDirectory(data.toFile(), null, null).equals(game.resolve("android").toFile()));
            check(GamePaths.treePath(root.toFile(), "My Game").equals(game.toFile().getCanonicalFile()));
            boolean blocked = false;
            try { GamePaths.treePath(root.toFile(), "../outside"); } catch (IOException expected) { blocked = true; }
            check(blocked);
            blocked = false;
            try { GamePaths.treePath(root.toFile(), "/outside"); } catch (IOException expected) { blocked = true; }
            check(blocked);
            System.out.println("PASS local folder discovery, sibling runtime and traversal rejection");
        } finally {
            try (var files = Files.walk(root)) { for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
}
