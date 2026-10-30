// =====================================================================
// Ayudantes para las pruebas unitarias
// Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)
//
// Las pruebas NO copian el código de los nodos: lo leen directamente de
// los workflows exportados de n8n (carpeta workflows/). Así se prueba
// exactamente el código que corre en n8n.
// =====================================================================
'use strict';
const fs = require('node:fs');
const path = require('node:path');

const RAIZ = path.resolve(__dirname, '..', '..');

function leerArchivo(relativo) {
  return fs.readFileSync(path.join(RAIZ, relativo), 'utf8');
}

function cargarWorkflow(archivo) {
  return JSON.parse(leerArchivo(path.join('workflows', archivo)));
}

function nodo(workflow, nombre) {
  const encontrado = workflow.nodes.find((n) => n.name === nombre);
  if (!encontrado) throw new Error(`El workflow "${workflow.name}" no tiene el nodo "${nombre}"`);
  return encontrado;
}

/**
 * Convierte el código de un nodo Code en una función que se puede
 * ejecutar fuera de n8n, simulando lo que n8n le entrega:
 *   $input -> los datos que llegan al nodo
 *   $      -> acceso a la salida de otros nodos: $('Nombre').all() / .first()
 *   $now   -> fecha y hora actual (Luxon en n8n)
 */
function nodoCode(archivo, nombre) {
  const codigo = nodo(cargarWorkflow(archivo), nombre).parameters.jsCode;
  const fn = new Function('$input', '$', '$now', codigo);
  return ({ entrada = [], otros = {}, ahora } = {}) => {
    const items = entrada.map((json) => ({ json }));
    const $input = { all: () => items, first: () => items[0] };
    const $ = (n) => {
      if (!(n in otros)) throw new Error(`La prueba no definió datos para el nodo "${n}"`);
      const datos = otros[n].map((json) => ({ json }));
      return { all: () => datos, first: () => datos[0] };
    };
    return fn($input, $, ahora).map((i) => i.json);
  };
}

/** Simulación mínima del objeto $now (Luxon) que usa el WF3. */
function ahoraFijo(iso) {
  const crear = (fecha) => ({
    minus: ({ hours = 0 }) => crear(new Date(fecha.getTime() - hours * 3600000)),
    toISO: () => fecha.toISOString(),
    toFormat: (formato) => {
      const p = (n) => String(n).padStart(2, '0');
      return formato
        .replace('yyyy', fecha.getUTCFullYear())
        .replace('MM', p(fecha.getUTCMonth() + 1))
        .replace('dd', p(fecha.getUTCDate()))
        .replace('HH', p(fecha.getUTCHours()))
        .replace('mm', p(fecha.getUTCMinutes()));
    },
  });
  return crear(new Date(iso));
}

/** Lee las categorías iniciales directamente de db/init/sql/schema.sql */
function categoriasDelEsquema() {
  const sql = leerArchivo(path.join('db', 'init', 'sql', 'schema.sql'));
  const filas = [...sql.matchAll(/\('([^']+)',\s*'[^']*',\s*ARRAY\[([^\]]*)\](?:::TEXT\[\])?,\s*'(ALTA|MEDIA|BAJA)'\)/g)];
  return filas.map((f, i) => ({
    id: i + 1,
    nombre: f[1],
    palabras_clave: [...f[2].matchAll(/'([^']+)'/g)].map((m) => m[1]),
    prioridad_base: f[3],
  }));
}

module.exports = { RAIZ, leerArchivo, cargarWorkflow, nodo, nodoCode, ahoraFijo, categoriasDelEsquema };
