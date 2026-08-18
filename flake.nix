{
  description = "Phoebe desktop packaged for NixOS";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      packages = import ./pkgs pkgs;
    in
    {
      packages.${system} = packages // {
        default = packages.phoebe;
      };

      overlays.default = final: prev: { inherit (import ./pkgs final) phoebe; };
    };
}
