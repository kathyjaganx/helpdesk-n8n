// Pruebas unitarias — WF3, nodos "Definir periodo" y "Armar reporte"
'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { nodoCode, ahoraFijo } = require('./ayudantes');

const definirPeriodo = nodoCode('WF3_reportes.json', 'Definir periodo');
const armarReporte = nodoCode('WF3_reportes.json', 'Armar reporte');

const PERIODO = { periodo_inicio: '', periodo_fin: '', nombre_archivo: 'reporte_tickets_20261029_1800.csv' };
const armar = (filas) => armarReporte({ entrada: filas, otros: { 'Definir periodo': [PERIODO] } })[0];
const ticket = (cambios) => ({
  codigo: 'TCK-000001', fecha: '2026-10-29 09:19', departamento: 'Contabilidad',
  asunto: 'No tengo internet', categoria: 'Red', prioridad: 'ALTA', estado: 'CLASIFICADO', ...cambios,
});

describe('WF3 · Definir periodo', () => {
  const r = definirPeriodo({ ahora: ahoraFijo('2026-10-29T18:00:00Z') })[0];

  it('el periodo cubre exactamente las últimas 24 horas', () => {
    assert.equal(new Date(r.periodo_fin) - new Date(r.periodo_inicio), 24 * 3600 * 1000);
  });

  it('el nombre del archivo lleva fecha y hora', () => {
    assert.equal(r.nombre_archivo, 'reporte_tickets_20261029_1800.csv');
  });
});

describe('WF3 · Armar reporte', () => {
  it('genera el encabezado y una fila por ticket', () => {
    const r = armar([ticket(), ticket({ codigo: 'TCK-000002' })]);
    const lineas = r.csv.replace('\uFEFF', '').split('\r\n');
    assert.equal(r.total, 2);
    assert.equal(lineas.length, 3);
    assert.equal(lineas[0], '"Codigo","Fecha","Departamento","Asunto","Categoria","Prioridad","Estado"');
  });

  it('empieza con BOM para que Excel muestre bien las tildes', () => {
    assert.ok(armar([ticket()]).csv.startsWith('\uFEFF'));
  });

  it('escapa las comillas dobles', () => {
    assert.match(armar([ticket({ asunto: 'Dijo "hola"' })]).csv, /"Dijo ""hola"""/);
  });

  it('conserva comas dentro de un campo', () => {
    assert.match(armar([ticket({ departamento: 'Ventas, zona 2' })]).csv, /"Ventas, zona 2"/);
  });

  it('genera el reporte aunque no haya tickets', () => {
    const r = armar([{}]);
    assert.equal(r.total, 0);
    assert.match(r.csv, /Sin tickets en el periodo/);
  });

  it('usa el nombre de archivo del periodo', () => {
    assert.equal(armar([ticket()]).nombre_archivo, PERIODO.nombre_archivo);
  });

  describe('seguridad: inyección de fórmulas en Excel', () => {
    for (const inicio of ['=', '+', '-', '@']) {
      it(`neutraliza una celda que empieza con "${inicio}"`, () => {
        const r = armar([ticket({ asunto: `${inicio}HYPERLINK("http://malo")` })]);
        assert.match(r.csv, new RegExp(`"'\\${inicio}HYPERLINK`));
      });
    }
  });
});
