xi-android
==========

An Android frontend for the [Xi] text editor.

Build and run
-------------

1. Set up rustc [for cross-compiling on Android], and build [xi-core][Xi] and [syntect] for Android with cargo, e.g. `cargo build --target i686-linux-android` for x86 Android
2. Put the resulting .so files inside the [jniLibs folder] `app/src/main/jniLibs/<target_arch>/`, target_arch is e.g. x86 or armeabi
3. Build the project normally with Android Studio

[Xi]: https://github.com/google/xi-editor
[for cross-compiling on Android]: https://blog.rust-lang.org/2016/05/13/rustup.html#example-running-rust-on-android
[jniLibs folder]: https://stackoverflow.com/questions/37116921/android-studio-include-and-consume-so-library
[syntect]: https://github.com/trishume/syntect
