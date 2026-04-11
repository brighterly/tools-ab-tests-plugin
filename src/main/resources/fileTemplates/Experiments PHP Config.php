<?php

return [
    'exp-1_example-experiment' => [
        'branches' => [
            'control' => 50,
            'variant' => 50,
        ],
        'start_date' => '${YEAR}-${MONTH}-${DAY}',
    ],
    'exp-2_closed-experiment' => [
        'branches' => [
            'original' => 80,
            'test' => 20,
        ],
        'override_branch' => 'original',
    ],
];
