CREATE TABLE process_draft (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  process_key VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(255),
  bpmn_xml TEXT NOT NULL,
  created_by VARCHAR(255),
  updated_by VARCHAR(255),
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);
