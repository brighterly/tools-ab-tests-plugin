# Experiments Plugin

PhpStorm plugin for working with A/B experiment keys defined in `config/experiments.php` or `config/experiments.json`.

## Features

- **Ctrl+click navigation** — jump from any experiment key usage (PHP, JS, TS) to its definition in the config file
- **Cmd+click from config** — click an experiment key in the config file to see all usages in a popup
- **Find Usages (Alt+F7)** — from a key in the config file, find all call sites across PHP, JS, and TS files
- **Inlay hints** — shows `[80/20]` branch distribution after each key at call sites
- **Hover documentation** — branch percentages, closed status, start date on hover
- **Strikethrough** — closed experiments (those with `override_branch`) are crossed out at every usage
- **Status bar widget** — shows `Exp ✓ 15` when config is loaded, `Exp ⚠` when not configured or empty

All features work in PHP, JavaScript, and TypeScript files.

## Configuration

Go to **Settings → Tools → AB Tests** and add one or more config file paths. Each entry has a path and a type (PHP or JSON).

If no paths are configured, the plugin auto-detects `config/experiments.php` and `config/experiments.json` in the open project root.

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

## Requirements

- PhpStorm 2026.1+
- PHP plugin (bundled with PhpStorm)

## Note on IDE restart

Updating the plugin requires an IDE restart. This is enforced by IntelliJ's extension point system and cannot be avoided.
