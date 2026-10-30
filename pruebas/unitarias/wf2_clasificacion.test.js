// Pruebas unitarias — WF2, nodo "Clasificar y priorizar"
// Las categorías se leen de db/init/sql/schema.sql: si alguien cambia las
// palabras clave en la base de datos, estas pruebas lo detectan.
'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { nodoCode, categoriasDelEsquema } = require('./ayudantes');

const clasificarNodo = nodoCode('WF2_clasificacion.json', 'Clasificar y priorizar');
const CATEGORIAS = categoriasDelEsquema();

function clasificar(asunto, descripcion = 'Sin más detalles por ahora') {
  const ticket = { id: 1, codigo: 'TCK-000001', asunto, descripcion };
  return clasificarNodo({ entrada: CATEGORIAS, otros: { 'Leer tickets nuevos': [ticket] } })[0];
}

describe('WF2 · Clasificar y priorizar', () => {
  it('lee las 5 categorías del esquema', () => {
    assert.deepEqual(CATEGORIAS.map((c) => c.nombre), ['Red', 'Accesos', 'Hardware', 'Software', 'Otros']);
  });

  describe('categoría', () => {
    const casos = [
      ['No tengo internet', 'Red', 'ALTA'],
      ['Olvidé mi contraseña', 'Accesos', 'MEDIA'],
      ['Impresora atascada', 'Hardware', 'MEDIA'],
      ['Necesito instalar Excel', 'Software', 'BAJA'],
      ['Silla de escritorio', 'Otros', 'BAJA'],
    ];
    for (const [asunto, categoria, prioridad] of casos) {
      it(`"${asunto}" → ${categoria} / ${prioridad}`, () => {
        const r = clasificar(asunto);
        assert.equal(r.categoria, categoria);
        assert.equal(r.prioridad, prioridad);
      });
    }
  });

  describe('reglas', () => {
    it('ignora mayúsculas y tildes', () => {
      assert.equal(clasificar('CONTRASEÑA BLOQUEADA').categoria, 'Accesos');
      assert.equal(clasificar('olvide mi contrasena').categoria, 'Accesos');
    });

    it('compara palabras completas: "Redactar" no es la categoría Red', () => {
      assert.equal(clasificar('Redactar informe', 'Necesito ayuda para redactar el informe anual').categoria, 'Otros');
    });

    it('el asunto pesa el doble que la descripción', () => {
      const r = clasificar('Impresora no responde', 'Creo que es por el wifi de la oficina');
      assert.equal(r.categoria, 'Hardware');
    });

    it('en empate gana la categoría de menor id', () => {
      assert.equal(clasificar('Revisar', 'el wifi y la impresora').categoria, 'Red');
    });

    it('una palabra de urgencia eleva la prioridad a ALTA', () => {
      const r = clasificar('Cuenta bloqueada', 'Es urgente, no puedo facturar');
      assert.equal(r.categoria, 'Accesos');
      assert.equal(r.prioridad, 'ALTA');
      assert.match(r.motivo, /urgencia: urgente/);
    });

    it('"no funciona" también es urgencia, aun en la categoría Otros', () => {
      assert.equal(clasificar('Aire acondicionado', 'El aire no funciona en la sala').prioridad, 'ALTA');
    });

    it('explica el motivo de la clasificación', () => {
      assert.match(clasificar('No tengo internet', 'El wifi no conecta').motivo, /^Categoría Red por: .*internet.*wifi/);
    });

    it('el motivo nunca supera 300 caracteres', () => {
      const largo = Array(80).fill('impresora monitor teclado').join(' ');
      assert.ok(clasificar('Impresora', largo).motivo.length <= 300);
    });

    it('clasifica varios tickets en una sola ejecución', () => {
      const tickets = [
        { id: 1, codigo: 'TCK-000001', asunto: 'Sin internet', descripcion: 'Desde hoy' },
        { id: 2, codigo: 'TCK-000002', asunto: 'Instalar Excel', descripcion: 'Para reportes' },
      ];
      const r = clasificarNodo({ entrada: CATEGORIAS, otros: { 'Leer tickets nuevos': tickets } });
      assert.deepEqual(r.map((x) => [x.ticket_id, x.categoria]), [[1, 'Red'], [2, 'Software']]);
    });
  });
});
