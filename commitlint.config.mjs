// The rules for the commit messages of a pull request, checked by .github/workflows/commits.yaml
export default {
  extends: ['@commitlint/config-conventional'],
  // The default ignore list of commitlint stays on. These two patterns name the merge commits of this repository on purpose.
  ignores: [
    (message) => /^Merge branch 'main' into \S+$/.test(message),
    (message) => /^Merge pull request #\d+ from \S+/.test(message),
  ],
  rules: {
    // Long commit bodies write long lines, and the summary is the part that counts
    'body-max-line-length': [0],
    'footer-max-line-length': [0],
    'footer-leading-blank': [0],
    'header-max-length': [2, 'always', 120],
  },
}
