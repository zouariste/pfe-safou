import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { globalIgnores } from 'eslint/config'

export default tseslint.config([
  // public/jsparsons is the vendored js-parsons library and its dependencies, not our code.
  globalIgnores(['dist', 'public/jsparsons']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    rules: {
      // Context modules export their hooks beside the provider, the usual context pattern.
      'react-refresh/only-export-components': [
        'error',
        { allowConstantExport: true, allowExportNames: ['useAuth', 'useSolveActivity'] },
      ],
    },
  },
])
