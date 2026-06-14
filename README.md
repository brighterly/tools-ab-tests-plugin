# Experiments Plugin

PhpStorm plugin for working with A/B experiment keys. Experiments are fetched from per-environment config URLs (development, staging, sandbox, production, local); legacy local `config/experiments.php` / `experiments.json` files are still supported but deprecated.

## Features

- **Ctrl+click navigation** — jump from any experiment key usage (PHP, JS, TS) to its definition in the config file
- **Cmd+click from config** — click an experiment key in the config file to see all usages in a popup
- **Find Usages (Alt+F7)** — from a key in the config file, find all call sites across PHP, JS, and TS files
- **Inlay hints** — shows `[80/20]` branch distribution after each key at call sites
- **Hover documentation** — branch percentages, closed status, start date on hover
- **Strikethrough** — closed experiments (those with `override_branch`) are crossed out at every usage
- **Status bar widget** — shows `Exp ✓ 15` when config is loaded; click to see per-file experiment counts and open config files directly
- **File stubs** — generate a new config file with example experiments via **right-click → New → Experiments Config**

All features work in PHP, JavaScript, and TypeScript files.

## Configuration

Go to **Settings → Tools → AB Tests**:

- **Environments** — each row is a label + a config URL (e.g. `https://storage.googleapis.com/brighterly-dev-configs/configs.json`). Pick the **active environment**; its URL is the source of experiments.
- **Active environment** can also be switched from the status bar widget, which offers **Sync now** to re-fetch.
- **Legacy file configs (deprecated)** — tick *Show experiments from local files (deprecated)* to also read `experiments.php` / `experiments.json` paths. Off by default. On a key conflict, the environment URL wins.

The remote JSON nests experiments under an `"experiments"` key; legacy local files keep `exp-*` keys at the top level. Both are supported.

## Config file formats

### PHP

```php
return [
    'exp-1_example-experiment' => [
        'branches' => [
            'control' => 50,
            'variant' => 50,
        ],
        'start_date' => '2024-01-15',
    ],
    'exp-2_closed-experiment' => [
        'branches' => [
            'control' => 80,
            'variant' => 20,
        ],
        'override_branch' => 'control', // closed experiment
    ],
];
```

### JSON

```json
{
    "exp-1_example-experiment": {
        "branches": [
            { "value": "control", "percentage": 50 },
            { "value": "variant", "percentage": 50 }
        ]
    },
    "exp-2_closed-experiment": {
        "branches": [
            { "value": "control", "percentage": 80 },
            { "value": "variant", "percentage": 20 }
        ],
        "override_branch": "control"
    }
}
```

Multiple config files (mixing PHP and JSON) can be active simultaneously — experiments are merged from all of them.

## Generating config file stubs

Right-click any directory in the project tree → **New → Experiments Config**, enter a filename (e.g. `experiments`), and choose **PHP** or **JSON**. The file is created with two example experiments — one open, one closed.

The templates are also editable in **Settings → Editor → File and Code Templates → Other → Experiments PHP Config / Experiments JSON Config**.

## Requirements

- PhpStorm 2026.1+
- PHP plugin (bundled with PhpStorm)

## Note on IDE restart

Updating the plugin requires an IDE restart. This is enforced by IntelliJ's extension point system and cannot be avoided.
