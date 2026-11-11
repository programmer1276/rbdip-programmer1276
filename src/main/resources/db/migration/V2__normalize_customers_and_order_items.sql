-- Контактные данные хранятся один раз для одной комбинации имени, адреса и телефона.
-- NULL в необязательных контактах тоже участвует в проверке уникальности.
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(50),
    CONSTRAINT customers_contacts_unique UNIQUE NULLS NOT DISTINCT (full_name, address, phone)
);

INSERT INTO customers (full_name, address, phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone
FROM orders;

ALTER TABLE orders ADD COLUMN customer_id BIGINT;

UPDATE orders o
SET customer_id = c.id
FROM customers c
WHERE c.full_name = o.customer_full_name
  AND c.address IS NOT DISTINCT FROM o.customer_address
  AND c.phone IS NOT DISTINCT FROM o.customer_phone;

ALTER TABLE orders
    ALTER COLUMN customer_id SET NOT NULL,
    ADD CONSTRAINT orders_customer_fk FOREIGN KEY (customer_id) REFERENCES customers(id);
CREATE INDEX orders_customer_id_idx ON orders(customer_id);

-- В V1 нет product_id. Восстанавливаем связь по сохранённым имени и цене.
-- Если такой версии товара уже нет в каталоге, сохраняем её отдельной записью.
INSERT INTO products (name, price)
SELECT DISTINCT i.product_name, i.product_price
FROM order_items i
WHERE NOT EXISTS (
    SELECT 1 FROM products p
    WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ADD COLUMN product_id BIGINT;

-- При одинаковых имени и цене в каталоге выбор связи детерминирован.
UPDATE order_items i
SET product_id = (
    SELECT MIN(p.id) FROM products p
    WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items
    ALTER COLUMN product_id SET NOT NULL,
    ADD CONSTRAINT order_items_product_fk FOREIGN KEY (product_id) REFERENCES products(id);
CREATE INDEX order_items_product_id_idx ON order_items(product_id);

ALTER TABLE orders
    DROP COLUMN customer_full_name,
    DROP COLUMN customer_address,
    DROP COLUMN customer_phone;
ALTER TABLE order_items
    DROP COLUMN product_name,
    DROP COLUMN product_price;
