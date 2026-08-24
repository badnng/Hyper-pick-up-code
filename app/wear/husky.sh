npx husky install
npx husky add .husky/pre-commit 'npx lint-staged'
npx husky add .husky/commit-msg 'npx --no -- commitlint --edit ${1}'