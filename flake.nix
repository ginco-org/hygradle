{
  description = "hygradle — Gradle plugin for Hytale plugin development";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          name = "hygradle-dev";

          packages = with pkgs; [
            jdk17 # plugin toolchain + Gradle runtime (matches CI)
            jdk25 # toolchain the plugin configures for consuming Hytale plugin projects

            # Language servers
            kotlin-language-server
          ];
          JAVA_HOME = pkgs.jdk17.home;
        };
      }
    );
}
