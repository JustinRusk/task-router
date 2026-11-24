-- Sample queues and routing rules for local practice. Safe to re-run.

INSERT INTO queues (name) VALUES
    ('billing'),
    ('tech-support'),
    ('spanish'),
    ('general')
ON CONFLICT (name) DO NOTHING;

INSERT INTO routing_rules (name, priority, dialed_number, caller_prefix, target_queue_id) VALUES
    ('Spanish line',            10, '+18005550199', NULL,    (SELECT id FROM queues WHERE name = 'spanish')),
    ('Billing line',            20, '+18005550100', NULL,    (SELECT id FROM queues WHERE name = 'billing')),
    ('Tech line, Austin area',  30, '+18005550111', '+1512', (SELECT id FROM queues WHERE name = 'tech-support')),
    ('Catch-all',             1000, NULL,           NULL,    (SELECT id FROM queues WHERE name = 'general'))
ON CONFLICT (priority) DO NOTHING;
