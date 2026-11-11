-- Триггер из expand-шагa заполняет новые поля без изменения исходного имени.
UPDATE customers SET full_name = full_name WHERE first_name IS NULL OR last_name IS NULL;

ALTER TABLE customers ALTER COLUMN first_name SET NOT NULL, ALTER COLUMN last_name SET NOT NULL;
ALTER TABLE customers ADD CONSTRAINT customers_name_contacts_unique
    UNIQUE NULLS NOT DISTINCT (first_name, last_name, address, phone);

-- С этого шага можно постепенно переводить приложения на first_name/last_name.
-- Старые приложения продолжают читать и писать full_name через переходный триггер.
