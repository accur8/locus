{
  description = "Run Claude CLI from Nix";

  inputs = {
    # Latest nixpkgs – needed because the CLI is still moving fast
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

    # numtide devshell
    devshell.url = "github:numtide/devshell";

    # Always‑up‑to‑date package for Claude Code (bundles its own Node runtime)
    claude-code.url = "github:sadjow/claude-code-nix";
  };

  outputs = { self, nixpkgs, claude-code, devshell, ... }:
    let
      # Pick your host; change to "aarch64-linux", "x86_64-darwin", … if needed
      system = "aarch64-darwin";

      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;      # CLI is proprietary
        overlays = [ devshell.overlays.default ];
      };

      my-java = pkgs.zulu17;
      my-sbt = pkgs.sbt.override { jre = my-java; };

      ammonite = pkgs.ammonite.override {
        jre = my-java;
      };

    in
    {
      # Makes `nix run` work
      apps.${system}.default = {
        type = "app";
        program = "${pkgs.claude-code}/bin/claude";
      };

      # Exposes it as a normal package (optional)
      packages.${system}.default = pkgs.claude-code;

      # Dev‑shell so the binary is on $PATH when you `nix develop`
      devShells.${system}.default = pkgs.devshell.mkShell {
        name = "locus";

        packages = [
          pkgs.claude-code
          pkgs.scala-next
          pkgs.nodejs_20   # or pkgs.nodejs_18 if needed
          my-java
          my-sbt
          ammonite
        ];
      };

      # Overlay from the external flake (keeps you on the latest release)
      nixosModules.default = { ... }:
        { nixpkgs.overlays = [ claude-code.overlays.default ]; };
    };
}