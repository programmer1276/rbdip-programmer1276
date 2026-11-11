-- Старое приложение продолжает работать с full_name.
ALTER TABLE customers ADD COLUMN first_name VARCHAR(255), ADD COLUMN last_name VARCHAR(255);

CREATE FUNCTION sync_customer_names() RETURNS TRIGGER AS $$
DECLARE
    separator INTEGER;
BEGIN
    IF NEW.first_name IS NULL OR NEW.last_name IS NULL
       OR (TG_OP = 'UPDATE' AND NEW.full_name IS DISTINCT FROM OLD.full_name) THEN
        separator := position(' ' IN NEW.full_name);
        -- Один компонент имени или завершающий пробел сохраняем целиком.
        IF separator > 1 AND separator < length(NEW.full_name) THEN
            NEW.first_name := substring(NEW.full_name FROM 1 FOR separator - 1);
            NEW.last_name := substring(NEW.full_name FROM separator + 1);
        ELSE
            NEW.first_name := NEW.full_name;
            NEW.last_name := '';
        END IF;
    ELSE
        NEW.full_name := NEW.first_name
            || CASE WHEN NEW.last_name = '' THEN '' ELSE ' ' || NEW.last_name END;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER customers_sync_names
BEFORE INSERT OR UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION sync_customer_names();
