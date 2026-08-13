CREATE SCHEMA IF NOT EXISTS sales;
CREATE SCHEMA IF NOT EXISTS hr;

CREATE TABLE IF NOT EXISTS sales.customer (
    customer_id   INT PRIMARY KEY,
    company_name  VARCHAR(120) NOT NULL,
    contact_email VARCHAR(120),
    vat_number    VARCHAR(20) UNIQUE
);

CREATE TABLE IF NOT EXISTS sales.invoice (
    invoice_id  INT PRIMARY KEY,
    customer_id INT NOT NULL REFERENCES sales.customer (customer_id),
    issued_on   DATE NOT NULL,
    total_eur   DECIMAL(12, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS hr.department (
    department_id INT PRIMARY KEY,
    department_name VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS hr.employee (
    employee_id   INT PRIMARY KEY,
    department_id INT REFERENCES hr.department (department_id),
    last_name     VARCHAR(80) NOT NULL,
    hired_on      DATE,
    monthly_wage  DECIMAL(10, 2)
);

-- cross-schema foreign key: hr.account_manager -> sales.customer
CREATE TABLE IF NOT EXISTS hr.account_manager (
    employee_id INT NOT NULL REFERENCES hr.employee (employee_id),
    customer_id INT NOT NULL REFERENCES sales.customer (customer_id),
    PRIMARY KEY (employee_id, customer_id)
);

-- composite primary key, only in hr
CREATE TABLE IF NOT EXISTS hr.employee_skill (
    employee_id INT NOT NULL REFERENCES hr.employee (employee_id),
    skill_code  VARCHAR(20) NOT NULL,
    level       INT,
    PRIMARY KEY (employee_id, skill_code)
);
