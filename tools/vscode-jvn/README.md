# JVN Language Tools

VS Code syntax highlighting and snippets for Java Vector Nexus project files.

## Included Languages

- VNS story scripts (`.vns`)
- JES scene and animation scripts (`.jes`)
- Story Map files (`.storymap`, legacy `.timeline`)
- JVN config files (`.layout`, `.menu`, `.registry`, `.settings`, `.stagepreset`, `.style`, `.theme`, `jvn.project`)

## DSL Icon Set

SVG sources live in `images/dsl/`; the README uses PNG previews because the VS Code Marketplace blocks SVG images in extension READMEs.

| DSL | Icon | Files |
| --- | --- | --- |
| VNS | <img src="images/dsl/vns.png" width="56" alt="VNS icon"> | `.vns` |
| JES | <img src="images/dsl/jes.png" width="56" alt="JES icon"> | `.jes` |
| Story Map | <img src="images/dsl/storymap.png" width="56" alt="Story Map icon"> | `.storymap`, `.timeline` |
| Menu | <img src="images/dsl/menu.png" width="56" alt="Menu icon"> | `.menu`, `.registry` |
| Layout | <img src="images/dsl/layout.png" width="56" alt="Layout icon"> | `.layout` |
| Style / Theme | <img src="images/dsl/style.png" width="56" alt="Style icon"> | `.style`, `.theme` |
| Stage Preset | <img src="images/dsl/stagepreset.png" width="56" alt="Stage Preset icon"> | `.stagepreset` |

## Local Development

From this folder:

```sh
code --extensionDevelopmentPath="$PWD"
```

To install it into your local VS Code extensions folder without packaging:

```sh
mkdir -p "$HOME/.vscode/extensions/jvn-language-tools"
rsync -a --delete ./ "$HOME/.vscode/extensions/jvn-language-tools/"
```

Restart VS Code after copying.

## Install From VSIX

Package the extension:

```sh
npm run package
```

Then install the generated `.vsix`:

```sh
code --install-extension jvn-language-tools-0.1.2.vsix
```

Users can also install it through VS Code's Extensions view with **Install from VSIX...**.

## Packaging

This extension uses the official VS Code extension packaging tool through `npx`:

```sh
npm run package
```

## Publish To Marketplace

1. Create or choose the Visual Studio Marketplace publisher id `Simplector`.
2. Make sure `publisher` in `package.json` matches that id.
3. Create an Azure DevOps personal access token with Marketplace manage scope.
4. Log in once:

```sh
npx @vscode/vsce login Simplector
```

5. Publish:

```sh
npm run publish
```

This first version is intentionally passive: it adds highlighting, bracket/comment behavior, and snippets without a runtime extension host. Diagnostics and language-server features should stay in the JVN editor until we have a stable external analyzer API.
