{
  description = "music-feature-consistency dev environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfreePredicate = pkg:
            builtins.elem (pkgs.lib.getName pkg) [ "terraform" ];
        };
      in
      {
        devShells = {
          default = pkgs.mkShell {
            packages = with pkgs; [
              # Infrastructure
              terraform
              kubectl
              kind
              kubernetes-helm
              jq
              yq-go
              awscli2
            ];

            shellHook = ''
              export KUBECONFIG="$PWD/terraform/kubeconfig"
            '';
          };

          java = pkgs.mkShell {
            packages = with pkgs; [
              jdk21
              gradle
            ];

            shellHook = ''
              export JAVA_HOME="${pkgs.jdk21}"
            '';
          };

          python = pkgs.mkShell {
            packages = with pkgs; [
              python312
              uv
            ];

            env.LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
              pkgs.stdenv.cc.cc.lib
              pkgs.zlib
            ];
          };
        };
      });
}
