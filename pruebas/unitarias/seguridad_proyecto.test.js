// Pruebas de seguridad sobre la configuración del proyecto completo.
// Revisan los archivos tal como están en el repositorio.
'use strict';
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { RAIZ, leerArchivo, cargarWorkflow } = require('./ayudantes');

const ARCHIVOS = fs.readdirSync(path.join(RAIZ, 'workflows')).filter((f) => f.endsWith('.json'));
const WORKFLOWS = ARCHIVOS.map((f) => ({ archivo: f, wf: cargarWorkflow(f) }));
const nodosDe = (tipo) => WORKFLOWS.flatMap(({ archivo, wf }) =>
  wf.nodes.filter((n) => n.type === tipo).map((n) => ({ archivo, n })));

describe('Seguridad · workflows', () => {
  it('existen los 5 workflows', () => {
    assert.equal(ARCHIVOS.length, 5);
  });

  describe('ninguna consulta SQL se arma pegando texto (inyección SQL)', () => {
    for (const { archivo, n } of nodosDe('n8n-nodes-base.postgres')) {
      it(`${archivo} · ${n.name}`, () => {
        assert.doesNotMatch(n.parameters.query, /\{\{|\$json|\$\(/, 'la consulta contiene una expresión de n8n');
        if (/\$\d/.test(n.parameters.query)) {
          assert.ok(n.parameters.options?.queryReplacement, 'usa $1, $2... pero no define los parámetros');
        }
      });
    }
  });

  describe('todos los webhooks exigen token (Header Auth)', () => {
    for (const { archivo, n } of nodosDe('n8n-nodes-base.webhook')) {
      it(`${archivo} · ${n.name}`, () => {
        assert.equal(n.parameters.authentication, 'headerAuth');
      });
    }
  });

  describe('los workflows principales tienen asignado el WF4 como Error Workflow', () => {
    for (const { archivo, wf } of WORKFLOWS.filter(({ archivo: a }) => !a.startsWith('WF4'))) {
      it(archivo, () => {
        assert.ok(wf.settings?.errorWorkflow, 'falta settings.errorWorkflow');
      });
    }
  });

  it('los workflows exportados no contienen contraseñas ni tokens', () => {
    for (const archivo of ARCHIVOS) {
      // n8n agrega al exportar un identificador de la instalación (instanceId)
      // de 64 caracteres; no es un secreto, así que se excluye de la revisión.
      const texto = leerArchivo(path.join('workflows', archivo))
        .replace(/"instanceId"\s*:\s*"[0-9a-f]{64}"/g, '');
      assert.doesNotMatch(texto, /"password"\s*:/i, archivo);
      assert.doesNotMatch(texto, /[0-9a-f]{64}/, `${archivo} contiene algo con forma de token`);
    }
  });
});

describe('Seguridad · frontend y nginx', () => {
  const app = leerArchivo(path.join('frontend', 'app.js'));
  const nginx = leerArchivo(path.join('nginx', 'default.conf.template'));

  it('la página nunca inserta datos como HTML (XSS)', () => {
    assert.doesNotMatch(app, /\.innerHTML\s*=|insertAdjacentHTML|document\.write/);
  });

  it('la página no contiene el token', () => {
    assert.doesNotMatch(app, /X-API-Key|[0-9a-f]{64}/i);
  });

  it('nginx agrega el token desde una variable de entorno', () => {
    assert.match(nginx, /proxy_set_header X-API-Key "\$\{HELPDESK_API_TOKEN\}"/);
  });

  it('nginx envía la política de seguridad de contenido (CSP)', () => {
    assert.match(nginx, /Content-Security-Policy "default-src 'self'/);
  });

  it('nginx limita la cantidad de tickets por minuto', () => {
    assert.match(nginx, /limit_req_zone .* rate=\d+r\/m/);
    assert.match(nginx, /limit_req_status 429/);
  });

  it('nginx solo permite POST para crear y GET para consultar', () => {
    assert.match(nginx, /location = \/api\/tickets \{[^}]*limit_except POST/);
    assert.match(nginx, /location = \/api\/consulta \{[^}]*limit_except GET/);
  });
});

describe('Seguridad · repositorio', () => {
  it('el archivo .env está excluido de Git', () => {
    assert.match(leerArchivo('.gitignore'), /^\.env$/m);
  });
});
