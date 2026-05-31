{
  description = "FT8AF Android development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      supportedSystems = [
        "aarch64-darwin"
        "aarch64-linux"
        "x86_64-darwin"
        "x86_64-linux"
      ];

      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;

      mkAndroidAbi =
        system:
        if nixpkgs.lib.hasPrefix "aarch64-" system then
          "arm64-v8a"
        else
          "x86_64";
    in
    {
      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              android_sdk.accept_license = true;
              allowUnfree = true;
            };
          };

          androidPlatformVersion = "35";
          androidBuildToolsVersion = "35.0.0";
          androidAbi = mkAndroidAbi system;

          androidComposition = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ androidPlatformVersion ];
            buildToolsVersions = [
              "34.0.0"
              androidBuildToolsVersion
            ];
            abiVersions = [ androidAbi ];
            systemImageTypes = [ "google_apis_playstore" ];
            includeEmulator = true;
            includeSystemImages = true;
            includeNDK = "if-supported";
            includeCmake = "if-supported";
          };

          androidSdk = androidComposition.androidsdk;
          androidHome = "${androidSdk}/libexec/android-sdk";

          ft8afEmulator = pkgs.androidenv.emulateApp {
            name = "ft8af-emulator";
            platformVersion = androidPlatformVersion;
            abiVersion = androidAbi;
            systemImageType = "google_apis_playstore";
          };

          runFt8afEmulator = pkgs.writeShellApplication {
            name = "run-ft8af-emulator";
            runtimeInputs = [ ft8afEmulator ];
            text = ''
              exec run-test-emulator "$@"
            '';
          };
        in
        {
          default = pkgs.mkShell {
            packages = [
              pkgs.curl
              pkgs.git
              pkgs.jdk17
              androidSdk
              runFt8afEmulator
            ];

            ANDROID_HOME = androidHome;
            ANDROID_SDK_ROOT = androidHome;
            ANDROID_NDK_ROOT = "${androidHome}/ndk-bundle";
            JAVA_HOME = pkgs.jdk17.home;
            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidHome}/build-tools/${androidBuildToolsVersion}/aapt2";

            shellHook = ''
              export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"
              if [ -d "$ANDROID_HOME/cmake" ]; then
                export PATH="$(find "$ANDROID_HOME/cmake" -mindepth 2 -maxdepth 2 -type d -name bin | sort | tail -n1):$PATH"
              fi

              cat <<'EOF'
FT8AF Android dev shell
  Build:    cd ft8cn && ./gradlew testDebugUnitTest assembleDebug
  Emulator: run-ft8af-emulator
EOF
            '';
          };
        }
      );

      packages = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              android_sdk.accept_license = true;
              allowUnfree = true;
            };
          };

          androidPlatformVersion = "35";
          androidAbi = mkAndroidAbi system;
        in
        {
          emulator = pkgs.androidenv.emulateApp {
            name = "ft8af-emulator";
            platformVersion = androidPlatformVersion;
            abiVersion = androidAbi;
            systemImageType = "google_apis_playstore";
          };
        }
      );

      apps = forAllSystems (system: {
        emulator = {
          type = "app";
          program = "${self.packages.${system}.emulator}/bin/run-test-emulator";
          meta.description = "Launch the FT8AF Android emulator";
        };
      });
    };
}
