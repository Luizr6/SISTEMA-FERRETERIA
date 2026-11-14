INSERT INTO productos_categoria (id,nombre) VALUES (1,'Herramientas');
INSERT INTO productos_proveedor (id,razon_social,ruc,telefono) VALUES (1,'Proveedor de prueba','20000000001','999999999');
INSERT INTO cuentas_usuario (id,username,first_name,last_name,rol) VALUES (1,'admin_lab','Ana','Prueba','ADMIN');
INSERT INTO ventas_cliente (id,nombre,numero_documento) VALUES (1,'Cliente de prueba','12345678');
INSERT INTO productos_producto (id,codigo,nombre,unidad_medida,precio_compra,precio_venta,stock_actual,stock_minimo,activo,categoria_id,proveedor_id) VALUES
 (1,'MAR-001','Martillo','UNIDAD',10,20,2,5,true,1,1),
 (2,'CLA-001','Clavos','KILO',3,5,0,4,true,1,null),
 (3,'TAL-001','Taladro','UNIDAD',50,80,20,5,true,1,1),
 (4,'INA-001','Inactivo','UNIDAD',1,2,0,3,false,1,null),
 (5,'LIM-001','En minimo','UNIDAD',2,4,3,3,true,1,1);
-- Dia de Lima 2026-10-27: [05:00 UTC, 05:00 UTC del dia siguiente).
INSERT INTO ventas_venta (id,numero,fecha,subtotal,igv,total,estado,metodo_pago,origen,vendedor_id,cliente_id) VALUES
 (1,'B001-1','2026-10-27 05:00:00+00',100,18,118,'COMPLETADA','EFECTIVO','MOSTRADOR',1,1),
 (2,'B001-2','2026-10-28 04:59:59+00',50,9,59,'COMPLETADA','YAPE','WEB',1,null),
 (3,'B001-3','2026-10-27 16:00:00+00',200,36,236,'ANULADA','EFECTIVO','MOSTRADOR',1,1),
 (4,'B001-4','2026-10-27 04:59:59+00',1000,180,1180,'COMPLETADA','EFECTIVO','MOSTRADOR',1,null),
 (5,'B001-5','2026-10-28 05:00:00+00',1000,180,1180,'COMPLETADA','EFECTIVO','MOSTRADOR',1,null);
INSERT INTO ventas_detalleventa (id,venta_id,producto_id,cantidad,precio_unitario,subtotal) VALUES
 (1,1,1,5,20,100),(2,2,2,10,5,50),(3,3,1,10,20,200);
