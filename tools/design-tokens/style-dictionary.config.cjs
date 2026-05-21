/**
 * style-dictionary.config.cjs
 * Style Dictionary v4 configuration for design token pipeline.
 * Source: ADR-005, implementation-notes §8.1.
 *
 * Source: docs/design/_shared/tokens.json (W3C format, hand-edited by banking-designer only)
 * Output:
 *   - frontend/src/styles/tokens.scss  — compile-time SCSS variables
 *   - frontend/src/styles/tokens.css   — runtime CSS custom properties on :root
 *
 * Rules:
 *   - outputReferences: false — alias-flatten at build time (no var(--x) chains in generated CSS)
 *   - Reduced-motion block MANDATORY in tokens.css
 *   - CI: "npm run tokens:check" fails PR if outputs lag source
 *
 * @see docs/tech-lead/balance-comparison/adrs/ADR-005-design-token-consumption.md
 */

'use strict';

const path = require('path');

// Path to source tokens.json relative to repo root
const SOURCE_TOKENS = path.resolve(__dirname, '../../docs/design/_shared/tokens.json');
const SCSS_OUTPUT_PATH = path.resolve(__dirname, '../../frontend/src/styles/');
const CSS_OUTPUT_PATH = path.resolve(__dirname, '../../frontend/src/styles/');

const AUTO_GENERATED_HEADER = '/* AUTO-GENERATED FROM tokens.json — DO NOT HAND-EDIT — see ADR-005 */\n';

/**
 * Custom transform: flatten W3C token alias references to resolved values.
 * Ensures `grep -E 'var\(--' frontend/src/styles/tokens.css` returns zero.
 * Style Dictionary v4 handles alias resolution natively with outputReferences: false.
 */

module.exports = {
  source: [SOURCE_TOKENS],

  platforms: {
    // ---- SCSS emit: compile-time $variables for @media breakpoints, calc() ----
    scss: {
      transformGroup: 'scss',
      buildPath: SCSS_OUTPUT_PATH + '/',
      prefix: '',
      files: [
        {
          destination: 'tokens.scss',
          format: 'scss/variables',
          options: {
            outputReferences: false,   // ADR-005 §2.2: alias-flatten
            fileHeader: () => AUTO_GENERATED_HEADER,
          },
        },
      ],
    },

    // ---- CSS emit: runtime :root --custom-properties for component styles ----
    css: {
      transformGroup: 'css',
      buildPath: CSS_OUTPUT_PATH + '/',
      files: [
        {
          destination: 'tokens.css',
          format: 'css/variables',
          options: {
            outputReferences: false,   // ADR-005 §2.2: alias-flatten
            selector: ':root',
            fileHeader: () => AUTO_GENERATED_HEADER,
          },
        },
      ],
    },
  },
};
