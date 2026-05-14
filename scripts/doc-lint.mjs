#!/usr/bin/env node

/**
 * Doc-Lint: Validate JVN documentation for broken links, stale symbols, and assets.
 *
 * Usage:
 *   node scripts/doc-lint.mjs              # full check
 *   node scripts/doc-lint.mjs --links-only # only check links
 *   node scripts/doc-lint.mjs --strict     # fail on warnings
 */

import fs from 'fs';
import path from 'path';

// Configuration
const DOCS_ROOT = './docs';
const MODULES_ROOT = './modules';
const SEVERITY = { error: 0, warn: 1, info: 2 };

let issueCount = { error: 0, warn: 0, info: 0 };
let allIssues = [];

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

function log(severity, file, message) {
  const level = Object.keys(SEVERITY).find(k => SEVERITY[k] === severity);
  console.log(`[${level.toUpperCase()}] ${file}: ${message}`);
  allIssues.push({ severity, file, message });
  issueCount[level]++;
}

function findMarkdownFiles(dir) {
  let files = [];
  const items = fs.readdirSync(dir, { withFileTypes: true });
  for (const item of items) {
    const fullPath = path.join(dir, item.name);
    if (item.isDirectory()) {
      files = files.concat(findMarkdownFiles(fullPath));
    } else if (item.isFile() && item.name.endsWith('.md')) {
      files.push(fullPath);
    }
  }
  return files;
}

function readFile(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf-8');
  } catch (e) {
    return null;
  }
}

// ============================================================================
// LINK CHECKER
// ============================================================================

function checkLinks() {
  console.log('\n📎 Checking relative links...');

  const markdownFiles = findMarkdownFiles(DOCS_ROOT);
  const linkRegex = /\[([^\]]+)\]\(([^)]+)\)/g;

  for (const file of markdownFiles) {
    const content = readFile(file);
    if (!content) continue;

    let match;
    while ((match = linkRegex.exec(content)) !== null) {
      const linkText = match[1];
      const linkHref = match[2];

      // Skip external URLs
      if (linkHref.startsWith('http://') || linkHref.startsWith('https://')) {
        continue;
      }

      // Skip anchor-only links
      if (linkHref.startsWith('#')) {
        continue;
      }

      const linkPath = linkHref.split('#')[0];  // remove anchor
      const dir = path.dirname(file);
      const resolvedPath = path.resolve(dir, linkPath);

      if (!fs.existsSync(resolvedPath)) {
        log(SEVERITY.error, file, `Broken link: [${linkText}](${linkHref})`);
      }
    }
  }
}

// ============================================================================
// ANCHOR VALIDATOR
// ============================================================================

function checkAnchors() {
  console.log('\n⚓ Checking anchor links...');

  const markdownFiles = findMarkdownFiles(DOCS_ROOT);
  const anchorLinkRegex = /\[([^\]]+)\]\(([^)]+#([^)]+))\)/g;

  for (const file of markdownFiles) {
    const content = readFile(file);
    if (!content) continue;

    let match;
    while ((match = anchorLinkRegex.exec(content)) !== null) {
      const linkText = match[1];
      const linkHref = match[2];
      const anchor = match[3];

      // Extract relative path
      const parts = linkHref.split('#');
      const linkPath = parts[0];

      // Resolve target file
      let targetPath;
      if (linkPath === '') {
        targetPath = file;
      } else {
        const dir = path.dirname(file);
        targetPath = path.resolve(dir, linkPath);
      }

      if (!fs.existsSync(targetPath)) {
        log(SEVERITY.error, file, `Anchor target file not found: ${linkPath}`);
        continue;
      }

      // Check anchor exists in target
      const targetContent = readFile(targetPath);
      if (targetContent) {
        const expectedHeading = anchor.toLowerCase();
        // Simple check: look for heading with similar text
        // (full markdown anchor generation is complex; this is heuristic)
        const headingRegex = /^#+\s+.*/gm;
        let hasAnchor = false;

        let headingMatch;
        while ((headingMatch = headingRegex.exec(targetContent)) !== null) {
          const heading = headingMatch[0].toLowerCase();
          // Convert to anchor: lowercase, spaces→hyphens, special chars removed
          const headingAnchor = heading
            .replace(/^#+\s+/, '')  // remove markdown markers
            .toLowerCase()
            .replace(/[^\w\s-]/g, '')  // remove special chars
            .replace(/\s+/g, '-')  // spaces to hyphens
            .replace(/-+/g, '-');  // collapse multiple hyphens

          if (headingAnchor === expectedHeading ||
              heading.includes(expectedHeading.replace(/-/g, ' '))) {
            hasAnchor = true;
            break;
          }
        }

        if (!hasAnchor) {
          log(SEVERITY.warn, file,
            `Anchor may not exist in target: #${anchor} in ${linkPath}`);
        }
      }
    }
  }
}

// ============================================================================
// SYMBOL DRIFT DETECTOR
// ============================================================================

function checkSymbols() {
  console.log('\n📝 Checking symbol references (sampling)...');

  const markdownFiles = findMarkdownFiles(DOCS_ROOT);
  // Sample check on key files
  const keyFiles = [
    'docs/architecture/core/system-architecture.md',
    'docs/architecture/core/2d-engine.md',
    'docs/scripting/vns/overview/vns-scripting.md',
    'docs/scripting/jes/overview/jes-scripting.md',
  ];

  const backtickSymbolRegex = /`([A-Za-z_][A-Za-z0-9_.]*)`/g;

  for (const file of markdownFiles.filter(f =>
    keyFiles.some(key => f.endsWith(key)))) {
    const content = readFile(file);
    if (!content) continue;

    let match;
    const seenSymbols = new Set();

    while ((match = backtickSymbolRegex.exec(content)) !== null) {
      const symbol = match[1];

      // Skip common false positives
      if (symbol.includes('_') || symbol.length < 3) continue;
      if (seenSymbols.has(symbol)) continue;
      seenSymbols.add(symbol);

      // Quick grep check (approximate)
      const classNameFile = symbol.replace(/\./g, '/') + '.java';
      const found = findFileInModules(classNameFile);

      if (!found) {
        // Check if it's a known good symbol
        const knownSymbols = [
          'Engine', 'Scene', 'Entity2D', 'Camera2D', 'RigidBody2D',
          'VnScene', 'JesScene2D', 'TimelineRunner', 'Blitter2D',
          'AssetManager', 'AudioManager', 'Input', 'Renderer',
          'ApplicationConfig', 'FrameStats', 'SceneAccessor', 'Easing'
        ];

        if (!knownSymbols.includes(symbol) && symbol.length > 4) {
          log(SEVERITY.info, file,
            `Symbol unverified (may not exist): ${symbol} (sampling)`);
        }
      }
    }
  }
}

function findFileInModules(fileName) {
  try {
    const result = require('child_process')
      .execSync(`find ${MODULES_ROOT} -name "${fileName}" 2>/dev/null`,
        { encoding: 'utf-8' });
    return result.trim().length > 0;
  } catch (e) {
    return false;
  }
}

// ============================================================================
// ASSET LINK VALIDATOR
// ============================================================================

function checkAssets() {
  console.log('\n🖼️  Checking image asset references...');

  const markdownFiles = findMarkdownFiles(DOCS_ROOT);
  const imageRegex = /!\[([^\]]*)\]\(([^)]+)\)/g;

  for (const file of markdownFiles) {
    const content = readFile(file);
    if (!content) continue;

    let match;
    while ((match = imageRegex.exec(content)) !== null) {
      const imagePath = match[2];

      // Skip remote images
      if (imagePath.startsWith('http://') || imagePath.startsWith('https://')) {
        continue;
      }

      const dir = path.dirname(file);
      const resolvedPath = path.resolve(dir, imagePath);

      if (!fs.existsSync(resolvedPath)) {
        log(SEVERITY.warn, file, `Image not found: ${imagePath}`);
      }
    }
  }
}

// ============================================================================
// INDEX COVERAGE CHECK
// ============================================================================

function checkIndexCoverage() {
  console.log('\n📑 Checking INDEX.md coverage...');

  const indexPath = path.join(DOCS_ROOT, 'INDEX.md');
  const indexContent = readFile(indexPath);

  if (!indexContent) {
    log(SEVERITY.error, indexPath, 'INDEX.md not found');
    return;
  }

  const allFiles = findMarkdownFiles(DOCS_ROOT);
  const referencedFiles = new Set();

  // Extract all links from INDEX.md
  const linkRegex = /\]\(([^)]+)\)/g;
  let match;
  while ((match = linkRegex.exec(indexContent)) !== null) {
    const linkPath = match[1].split('#')[0];
    const fullPath = path.join(DOCS_ROOT, linkPath);
    referencedFiles.add(path.normalize(fullPath));
  }

  // Check for orphans
  const orphans = allFiles.filter(file => {
    if (file.includes('/generated-')) return false;  // ignore generated
    if (file.endsWith('README.md')) return false;    // readme at dir level OK
    if (file.endsWith('index.html')) return false;
    return !referencedFiles.has(path.normalize(file));
  });

  if (orphans.length > 0) {
    orphans.forEach(file => {
      const relPath = path.relative(DOCS_ROOT, file);
      log(SEVERITY.info, 'INDEX.md', `Orphan page not referenced: ${relPath}`);
    });
  }
}

// ============================================================================
// MAIN
// ============================================================================

async function main() {
  console.log('🚀 JVN Doc-Lint v1.0\n');
  console.log(`Checking docs in: ${path.resolve(DOCS_ROOT)}`);

  const args = process.argv.slice(2);
  const linksOnly = args.includes('--links-only');
  const strict = args.includes('--strict');

  if (!linksOnly) {
    checkLinks();
    checkAnchors();
    checkAssets();
    checkIndexCoverage();
    checkSymbols();
  } else {
    checkLinks();
  }

  // Summary
  console.log(`\n${'='.repeat(60)}`);
  console.log(`📊 Results: ${issueCount.error} errors, ${issueCount.warn} warnings, ${issueCount.info} info`);
  console.log(`${'='.repeat(60)}\n`);

  if (issueCount.error > 0) {
    console.error('❌ Doc-lint FAILED: Fix errors above');
    process.exit(1);
  } else if (issueCount.warn > 0 && strict) {
    console.error('⚠️  Doc-lint FAILED (strict mode): Fix warnings above');
    process.exit(1);
  } else {
    console.log('✅ Doc-lint PASSED');
    process.exit(0);
  }
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(2);
});
