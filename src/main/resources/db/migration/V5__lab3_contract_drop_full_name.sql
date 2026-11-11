-- Применять после переключения всех экземпляров приложения на новые поля.
DROP TRIGGER customers_sync_names ON customers;
DROP FUNCTION sync_customer_names();
ALTER TABLE customers DROP COLUMN full_name;
