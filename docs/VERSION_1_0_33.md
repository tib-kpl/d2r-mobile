# Switch 1.0.33.0 compatibility audit

The supplied 1.0.33.0 package was extracted locally together with the supplied base
game. No game program, archive, key, or generated game data is committed or uploaded.

| Module | Result |
|---|---|
| `main` | Build ID `194361731c7b9f30b38425b244429e25`; differs from the supported `db0507f36b84200ac05f75d76ed5026f` |
| `sdk` | SHA256 matches the SDK required by the existing extraction guide |
| `subsdk0` | SHA256 matches the module required by the existing extraction guide |
| French audio | Manifest lists two French archives; a French audio entry was decoded and its MD5 matched the manifest |

The expected instructions at two existing patch sites were compared with the
decompressed 1.0.33.0 main image. Both checks failed. The current rewriter's patch
profile and the host's hard-coded function/data addresses therefore cannot be
applied to this program just by accepting its version string or build ID.

Actual support requires a separate address profile, updated patch expectations,
validation of resource/layout/table fingerprints and shader generation, and tests
covering startup, character creation, playing, saving and reloading on hardware.
The audit does **not** establish 1.0.33.0 compatibility. The version gate remains
active; `dm/tools/game_version.py <Update>` reports this version explicitly before
the packaging tool starts its expensive work.
