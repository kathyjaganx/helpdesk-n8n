// Pruebas unitarias — WF4, nodo "Preparar registro"
'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { nodoCode } = require('./ayudantes');

const preparar = nodoCode('WF4_errores.json', 'Preparar registro');
const ejecutar = (datos) => preparar({ entrada: [datos] })[0];

const FALLA_EJECUCION = {
  execution: {
    id: '231', url: 'http://localhost:5678/workflow/abc/executions/231', mode: 'webhook',
    lastNodeExecuted: 'Guardar ticket',
    error: { message: 'permission denied for table tickets', stack: 'Error: permission denied\n    at /usr/lib/node_modules/...' },
  },
  workflow: { id: 'abc', name: 'WF1 - Ingesta de tickets' },
};

describe('WF4 · Preparar registro', () => {
  it('identifica workflow, nodo, mensaje y ejecución', () => {
    const r = ejecutar(FALLA_EJECUCION);
    assert.equal(r.workflow, 'WF1 - Ingesta de tickets');
    assert.equal(r.nodo, 'Guardar ticket');
    assert.equal(r.mensaje, 'permission denied for table tickets');
    assert.equal(r.ejecucion_id, '231');
    assert.equal(r.detalle.origen, 'ejecucion');
  });

  it('maneja una falla del disparador (sin ejecución)', () => {
    const r = ejecutar({
      trigger: { mode: 'trigger', error: { message: 'Cron falló', node: { name: 'Diario 18:00' } } },
      workflow: { id: 'x', name: 'WF3 - Generación de reportes' },
    });
    assert.equal(r.nodo, 'Diario 18:00');
    assert.equal(r.ejecucion_id, 'Sin ejecucion');
    assert.equal(r.detalle.origen, 'disparador');
  });

  it('usa valores por defecto si faltan datos', () => {
    const r = ejecutar({});
    assert.equal(r.workflow, 'Desconocido');
    assert.equal(r.mensaje, 'Error sin mensaje');
  });

  it('recorta el mensaje a 2000 caracteres', () => {
    const r = ejecutar({ execution: { error: { message: 'x'.repeat(5000) } } });
    assert.equal(r.mensaje.length, 2000);
  });

  it('seguridad: no guarda el stack técnico', () => {
    assert.doesNotMatch(JSON.stringify(ejecutar(FALLA_EJECUCION)), /node_modules|stack/);
  });
});
