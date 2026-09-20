CREATE DATABASE IF NOT EXISTS semantic_analytics;
USE semantic_analytics;

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    region     VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    category VARCHAR(50)  NOT NULL,
    price    DECIMAL(10,2) NOT NULL
);

CREATE TABLE orders (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    order_date  DATE NOT NULL,
    status      VARCHAR(20) NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE order_items (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    order_id   INT NOT NULL,
    product_id INT NOT NULL,
    quantity   INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id)   REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- 30 customers, 4 regions
INSERT INTO customers (name, region)
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 30)
SELECT CONCAT('Customer ', i),
       ELT(1 + (i % 4), 'Western', 'Eastern', 'Northern', 'Southern')
FROM n;

-- 30 products, 4 categories
INSERT INTO products (name, category, price)
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 30)
SELECT CONCAT('Product ', i),
       ELT(1 + (i % 4), 'Electronics', 'Clothing', 'Home', 'Sports'),
       10 + (i * 37 % 190)
FROM n;

-- 300 orders, 1 Jul 2026 to 20 Sep 2026
INSERT INTO orders (customer_id, order_date, status)
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 300)
SELECT 1 + FLOOR(POW(RAND(), 1.5) * 30),
       DATE_ADD('2026-07-01', INTERVAL FLOOR(RAND() * 82) DAY),
       'completed'
FROM n;

-- 3 different products per order
INSERT INTO order_items (order_id, product_id, quantity, unit_price)
SELECT o.id, p.id, 1 + (o.id + t.n) % 4, p.price
FROM orders o
JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2) t
JOIN products p ON p.id = 1 + ((o.id * 7 + t.n * 11) % 30);
