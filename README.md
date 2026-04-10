# Experiments Plugin

PhpStorm plugin for working with experiment keys defined in `config/experiments.php`.

## Features

- **Ctrl+click navigation** — jump from any experiment key usage to its definition in the config file
- **Find Usages (Alt+F7)** — from a key in the config file, find all call sites across the project
- **Inlay hints** — shows `[80/20]` branch distribution after each key at call sites
- **Hover documentation** — branch percentages, closed status, start date on hover
- **Strikethrough** — closed experiments (those with `override_branch`) are crossed out at every usage
- **Status bar widget** — shows `Exp ✓ 15` when config is loaded, `Exp ⚠` when not configured or empty

## Configuration

Go to **Settings → Tools → AB Tests** and set the path to your `config/experiments.php`.

If left blank, the plugin auto-detects `config/experiments.php` in the open project root.

## Config file format

```php
return [
    'exp-1_example-experiment-1' => [
        'branches' => [
            'control' => 50,
            'variant' => 50,
        ],
        'start_date' => '2024-01-15',
    ],
    'exp-2_example-experiment-2' => [
        'branches' => [
            'control' => 80,
            'variant' => 20,
        ],
        'override_branch' => 'control', // closed experiment
    ],
];
```

## Requirements

- PhpStorm 2026.1+
- PHP plugin (bundled with PhpStorm)
