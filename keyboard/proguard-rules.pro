# GwaBoard Keyboard — ProGuard rules
# Keep the IME service class (referenced by name in AndroidManifest.xml)
-keep class dev.gwaboard.keyboard.GwaBoardImeService { *; }
