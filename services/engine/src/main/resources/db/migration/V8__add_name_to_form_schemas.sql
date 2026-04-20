-- Add name column to form_schemas table
ALTER TABLE form_schemas ADD COLUMN name VARCHAR(255);

-- Populate name from description (extract part before " - ")
UPDATE form_schemas SET name = SPLIT_PART(description, ' - ', 1)
WHERE description IS NOT NULL AND description LIKE '% - %';

-- For rows without " - " in description, humanize the form_key
UPDATE form_schemas SET name = INITCAP(REPLACE(form_key, '-', ' '))
WHERE name IS NULL;
