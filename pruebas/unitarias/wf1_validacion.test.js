// Pruebas unitarias — WF1, nodo "Validar y sanitizar"
'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { nodoCode } = require('./ayudantes');

const validar = nodoCode('WF1_ingesta.json', 'Validar y sanitizar');
const ejecutar = (body, headers = {}) => validar({ entrada: [{ body, headers }] })[0];

const VALIDO = {
  solicitante_nombre: 'María García',
  solicitante_correo: 'maria.garcia@empresa.com',
  departamento: 'Contabilidad',
  asunto: 'No tengo internet',
  descripcion: 'Desde esta mañana no hay conexión wifi en mi computadora',
};
const con = (cambios) => ({ ...VALIDO, ...cambios });

describe('WF1 · Validar y sanitizar', () => {
  describe('datos válidos', () => {
    it('acepta un ticket completo', () => {
      const r = ejecutar(VALIDO);
      assert.equal(r.valido, true);
      assert.deepEqual(r.errores, []);
      assert.equal(r.datos.asunto, 'No tengo internet');
    });

    it('pasa el correo a minúsculas', () => {
      assert.equal(ejecutar(con({ solicitante_correo: 'Maria.Garcia@Empresa.COM' })).datos.solicitante_correo, 'maria.garcia@empresa.com');
    });

    it('quita espacios sobrantes y colapsa los repetidos', () => {
      assert.equal(ejecutar(con({ asunto: '   No    tengo   internet  ' })).datos.asunto, 'No tengo internet');
    });

    it('acepta tildes y eñes', () => {
      assert.equal(ejecutar(con({ solicitante_nombre: 'Íñigo Peña' })).datos.solicitante_nombre, 'Íñigo Peña');
    });

    it('acepta los límites exactos de longitud', () => {
      const r = ejecutar(con({ solicitante_nombre: 'Ana', descripcion: 'x'.repeat(2000) }));
      assert.equal(r.valido, true);
    });
  });

  describe('datos inválidos', () => {
    it('rechaza un correo sin arroba', () => {
      const r = ejecutar(con({ solicitante_correo: 'correo-sin-arroba.com' }));
      assert.equal(r.valido, false);
      assert.match(r.errores.join(), /solicitante_correo no tiene un formato válido/);
    });

    it('lista todos los campos obligatorios que faltan', () => {
      const r = ejecutar({ solicitante_nombre: 'Pedro' });
      assert.equal(r.valido, false);
      assert.equal(r.errores.length, 4);
    });

    it('rechaza una descripción de menos de 10 caracteres', () => {
      assert.match(ejecutar(con({ descripcion: 'falla' })).errores.join(), /al menos 10/);
    });

    it('rechaza una descripción de más de 2000 caracteres', () => {
      assert.match(ejecutar(con({ descripcion: 'x'.repeat(2001) })).errores.join(), /no puede superar 2000/);
    });

    it('rechaza valores que no son texto', () => {
      assert.match(ejecutar(con({ asunto: 12345 })).errores.join(), /asunto debe ser texto/);
    });

    it('rechaza un cuerpo que es una lista', () => {
      assert.match(ejecutar(['a', 'b']).errores.join(), /objeto JSON/);
    });

    it('rechaza un cuerpo vacío', () => {
      assert.equal(ejecutar(undefined).valido, false);
    });

    it('rechaza un campo que solo tiene espacios', () => {
      assert.equal(ejecutar(con({ asunto: '        ' })).valido, false);
    });
  });

  describe('seguridad', () => {
    it('elimina un bloque <script> completo', () => {
      const r = ejecutar(con({ asunto: "<script>alert('hack')</script>Impresora atascada" }));
      assert.equal(r.datos.asunto, 'Impresora atascada');
    });

    it('elimina etiquetas HTML', () => {
      assert.equal(ejecutar(con({ asunto: '<b>Monitor</b> <img src=x onerror=alert(1)>roto' })).datos.asunto, 'Monitor roto');
    });

    it('elimina caracteres de control', () => {
      assert.equal(ejecutar(con({ asunto: 'Sin\u0000 inter\u0007net' })).datos.asunto, 'Sin inter net');
    });

    it('ignora campos que no están en la lista blanca', () => {
      const r = ejecutar(con({ estado: 'CERRADO', prioridad: 'ALTA', id: 1 }));
      assert.equal(r.valido, true);
      assert.deepEqual(Object.keys(r.datos).sort(),
        ['asunto', 'departamento', 'descripcion', 'solicitante_correo', 'solicitante_nombre']);
    });

    it('conserva el texto de un intento de inyección SQL (lo neutraliza la consulta parametrizada)', () => {
      const texto = "Error'); DROP TABLE tickets;--";
      assert.equal(ejecutar(con({ descripcion: texto })).datos.descripcion, texto);
    });

    it('toma el origen de X-Forwarded-For', () => {
      assert.equal(ejecutar(VALIDO, { 'x-forwarded-for': '172.18.0.4' }).origen_ip, '172.18.0.4');
    });
  });
});
