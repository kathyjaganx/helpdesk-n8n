// Pruebas unitarias — WF5, nodo "Validar consulta"
'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { nodoCode } = require('./ayudantes');

const validar = nodoCode('WF5_consulta.json', 'Validar consulta');
const ejecutar = (query) => validar({ entrada: [{ query }] })[0];

describe('WF5 · Validar consulta', () => {
  it('sin parámetros devuelve los valores por defecto', () => {
    assert.deepEqual(ejecutar({}), { valido: true, errores: [], codigo: '', limite: 50 });
  });

  it('acepta un código válido y lo pasa a mayúsculas', () => {
    assert.equal(ejecutar({ codigo: ' tck-000012 ' }).codigo, 'TCK-000012');
  });

  it('acepta un límite dentro del rango', () => {
    assert.equal(ejecutar({ limite: '20' }).limite, 20);
  });

  it('ignora parámetros desconocidos', () => {
    assert.equal(ejecutar({ limite: '5', orden: 'DROP' }).valido, true);
  });

  for (const codigo of ['TCK-12', 'ABC-000001', 'TCK-0000012', "TCK-1' OR 1=1"]) {
    it(`rechaza el código "${codigo}"`, () => {
      const r = ejecutar({ codigo });
      assert.equal(r.valido, false);
      assert.equal(r.codigo, '');
    });
  }

  for (const limite of ['0', '201', '5000', 'abc', '2.5', '-1']) {
    it(`rechaza el límite "${limite}"`, () => {
      assert.equal(ejecutar({ limite }).valido, false);
    });
  }
});
