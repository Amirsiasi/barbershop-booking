-- Contact links for the website footer. phone and email already exist on shop.
ALTER TABLE shop
    ADD COLUMN instagram VARCHAR(30),   -- handle without @, e.g. mrartist.barbershop
    ADD COLUMN whatsapp  VARCHAR(20);   -- normalised number, e.g. +436601234567
