/* =====================================================================
   Mesa de ayuda — lógica del frontend
   Autora: Irma Yaritza Katiuxca Jagan Xol (4090-23-9748)

   El backend son los workflows de n8n. La página nunca habla con n8n
   directamente: llama a /api/... y nginx reenvía la petición agregando
   el token del lado del servidor. El navegador nunca ve el token.

     POST /api/tickets   -> WF1 (registrar ticket)
     GET  /api/consulta  -> WF5 (listar tickets y resumen)

   Seguridad: todos los datos se muestran con textContent, nunca con
   innerHTML, para que un texto malicioso no pueda ejecutarse (XSS).
   ===================================================================== */
'use strict';

const API = { crear: '/api/tickets', consulta: '/api/consulta' };
const REFRESCO_NORMAL = 15000;   // cada 15 s
const REFRESCO_RAPIDO = 5000;    // cada 5 s mientras se espera una clasificación
const ESPERA_MAXIMA = 150000;    // deja de acelerar a los 2.5 min

const CAMPOS = {
  solicitante_nombre: 'Nombre completo',
  solicitante_correo: 'Correo',
  departamento: 'Departamento',
  asunto: 'Asunto',
  descripcion: 'Descripción',
};
const NOMBRE_PRIORIDAD = { ALTA: 'Alta', MEDIA: 'Media', BAJA: 'Baja' };
const NOMBRE_ESTADO = {
  NUEVO: 'Nuevo', CLASIFICADO: 'Clasificado', EN_PROCESO: 'En proceso',
  RESUELTO: 'Resuelto', CERRADO: 'Cerrado',
};

const el = (id) => document.getElementById(id);

const estado = {
  tickets: [],
  resumen: null,
  codigoBuscado: '',
  ultimoCodigo: '',
  esperaDesde: 0,
  abiertos: new Set(),
  temporizador: null,
};

/* ---------------------------------------------------------------------
   Utilidades
   --------------------------------------------------------------------- */
function crear(etiqueta, clase, texto) {
  const nodo = document.createElement(etiqueta);
  if (clase) nodo.className = clase;
  if (texto !== undefined && texto !== null) nodo.textContent = texto;
  return nodo;
}

function clasePrioridad(prioridad) {
  if (prioridad === 'ALTA') return 'prioridad-alta';
  if (prioridad === 'MEDIA') return 'prioridad-media';
  if (prioridad === 'BAJA') return 'prioridad-baja';
  return 'prioridad-ninguna';
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/* "2026-10-29 10:42" -> "29 oct 10:42" */
function fechaCorta(texto) {
  const m = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}:\d{2})$/.exec(texto || '');
  if (!m) return texto || '';
  return `${Number(m[3])} ${MESES[Number(m[2]) - 1]} ${m[4]}`;
}

function horaActual() {
  return new Date().toLocaleTimeString('es-GT', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function marcarConexion(ok, texto) {
  const nodo = el('conexion');
  nodo.dataset.estado = ok ? 'ok' : 'error';
  el('conexion-texto').textContent = texto;
}

/* ---------------------------------------------------------------------
   Mensajes de validación: traduce los errores del WF1 a texto para
   personas y los coloca debajo del campo correspondiente.
   --------------------------------------------------------------------- */
function mensajeAmigable(error) {
  let m;
  if (/^Falta el campo/.test(error)) return 'Completa este campo.';
  if ((m = error.match(/al menos (\d+)/))) return `Escribe al menos ${m[1]} caracteres.`;
  if ((m = error.match(/superar (\d+)/))) return `Usa como máximo ${m[1]} caracteres.`;
  if (/formato válido/.test(error)) return 'Escribe un correo válido, por ejemplo nombre@empresa.com.';
  if (/debe ser texto/.test(error)) return 'El valor no es válido.';
  return error;
}

function limpiarErrores() {
  for (const campo of Object.keys(CAMPOS)) {
    el(`error-${campo}`).textContent = '';
    el(campo).removeAttribute('aria-invalid');
  }
  const mensaje = el('formulario-mensaje');
  mensaje.textContent = '';
  delete mensaje.dataset.tipo;
}

function mostrarErrores(errores) {
  const generales = [];
  for (const error of errores) {
    const campo = Object.keys(CAMPOS).find((c) => error.includes(c));
    if (campo) {
      el(`error-${campo}`).textContent = mensajeAmigable(error);
      el(campo).setAttribute('aria-invalid', 'true');
    } else {
      generales.push(error);
    }
  }
  const primero = Object.keys(CAMPOS).find((c) => el(c).getAttribute('aria-invalid') === 'true');
  if (primero) el(primero).focus();
  if (generales.length) mostrarMensaje('error', generales.join(' '));
}

function mostrarMensaje(tipo, texto) {
  const mensaje = el('formulario-mensaje');
  mensaje.dataset.tipo = tipo;
  mensaje.textContent = texto;
}

/* Validación rápida en el navegador. El servidor (WF1) vuelve a validar
   todo: esto solo evita viajes innecesarios. */
function validarLocal(datos) {
  const errores = [];
  const reglas = {
    solicitante_nombre: [3, 100], solicitante_correo: [6, 150],
    departamento: [2, 100], asunto: [5, 150], descripcion: [10, 2000],
  };
  for (const [campo, [min, max]] of Object.entries(reglas)) {
    const valor = datos[campo];
    if (!valor) errores.push(`Falta el campo ${campo}`);
    else if (valor.length < min) errores.push(`${campo} debe tener al menos ${min} caracteres`);
    else if (valor.length > max) errores.push(`${campo} no puede superar ${max} caracteres`);
  }
  if (datos.solicitante_correo && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(datos.solicitante_correo)) {
    errores.push('solicitante_correo no tiene un formato válido');
  }
  return errores;
}

/* ---------------------------------------------------------------------
   Registrar un ticket (WF1)
   --------------------------------------------------------------------- */
async function enviarTicket(evento) {
  evento.preventDefault();
  limpiarErrores();

  const formulario = el('formulario');
  const datos = {};
  for (const campo of Object.keys(CAMPOS)) datos[campo] = formulario.elements[campo].value.trim();

  const erroresLocales = validarLocal(datos);
  if (erroresLocales.length) {
    mostrarErrores(erroresLocales);
    return;
  }

  const boton = el('boton-enviar');
  boton.disabled = true;
  boton.textContent = 'Enviando…';

  try {
    const respuesta = await fetch(API.crear, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(datos),
    });
    const cuerpo = await respuesta.json().catch(() => ({}));

    if (respuesta.status === 201) {
      sellarCodigo(cuerpo.codigo);
      formulario.reset();
      actualizarContador();
      mostrarMensaje('ok', `Ticket ${cuerpo.codigo} registrado. Puedes seguirlo en la lista de la derecha.`);
      estado.ultimoCodigo = cuerpo.codigo;
      estado.esperaDesde = Date.now();
      cargarTickets();
    } else if (respuesta.status === 400) {
      mostrarErrores(cuerpo.errores || ['Revisa los datos del formulario.']);
    } else if (respuesta.status === 429) {
      mostrarMensaje('error', 'Enviaste demasiados tickets seguidos. Espera un minuto e inténtalo de nuevo.');
    } else if (respuesta.status === 403) {
      mostrarMensaje('error', 'El servidor rechazó la conexión con el sistema. Revisa que el token del archivo .env coincida con el de n8n.');
    } else {
      mostrarMensaje('error', 'El sistema no pudo registrar el ticket. La falla quedó registrada; inténtalo de nuevo en unos minutos.');
    }
  } catch (_) {
    mostrarMensaje('error', 'No hay conexión con el servidor. Revisa que los contenedores estén encendidos.');
  } finally {
    boton.disabled = false;
    boton.textContent = 'Enviar ticket';
  }
}

function sellarCodigo(codigo) {
  const talon = el('talon');
  el('talon-codigo').textContent = codigo;
  talon.classList.remove('registrado');
  void talon.offsetWidth; // reinicia la animación si se envía otro ticket
  talon.classList.add('registrado');
  el('talon-nota').textContent = 'Guárdalo para dar seguimiento. Se clasificará en menos de un minuto.';
}

function actualizarContador() {
  const largo = el('descripcion').value.length;
  el('contador').textContent = `${largo} / 2000`;
}

/* ---------------------------------------------------------------------
   Consultar tickets (WF5)
   --------------------------------------------------------------------- */
async function cargarTickets() {
  clearTimeout(estado.temporizador);
  const parametros = new URLSearchParams({ limite: '100' });
  if (estado.codigoBuscado) parametros.set('codigo', estado.codigoBuscado);

  try {
    const respuesta = await fetch(`${API.consulta}?${parametros}`, { headers: { Accept: 'application/json' } });
    const cuerpo = await respuesta.json().catch(() => ({}));

    if (respuesta.ok) {
      estado.tickets = Array.isArray(cuerpo.tickets) ? cuerpo.tickets : [];
      estado.resumen = cuerpo.resumen || null;
      marcarConexion(true, `Actualizado a las ${horaActual()}`);
      dibujar();
    } else if (respuesta.status === 400) {
      marcarConexion(true, `Actualizado a las ${horaActual()}`);
      mostrarVacio((cuerpo.errores || ['Consulta inválida.']).join(' '));
    } else {
      marcarConexion(false, 'El sistema no respondió. Reintentando…');
    }
  } catch (_) {
    marcarConexion(false, 'Sin conexión con el servidor. Reintentando…');
  } finally {
    programarSiguiente();
  }
}

function programarSiguiente() {
  if (document.hidden) return;
  const ticket = estado.tickets.find((t) => t.codigo === estado.ultimoCodigo);
  const esperando = estado.ultimoCodigo && (!ticket || ticket.estado === 'NUEVO')
    && Date.now() - estado.esperaDesde < ESPERA_MAXIMA;
  estado.temporizador = setTimeout(cargarTickets, esperando ? REFRESCO_RAPIDO : REFRESCO_NORMAL);
}

/* ---------------------------------------------------------------------
   Dibujar el resumen y la tabla
   --------------------------------------------------------------------- */
function dibujar() {
  dibujarResumen();
  dibujarTabla();
}

function dibujarResumen() {
  const r = estado.resumen;
  if (!r) return;
  el('resumen-total').textContent = r.total ?? 0;
  el('resumen-espera').textContent = (r.por_estado && r.por_estado.NUEVO) || 0;

  const conteo = r.por_prioridad || {};
  const total = Object.values(conteo).reduce((a, b) => a + b, 0) || 1;
  const leyenda = el('leyenda');
  leyenda.replaceChildren();
  const etiquetas = [];

  for (const tramo of el('reparto').children) {
    const clave = tramo.dataset.prioridad;
    const n = conteo[clave] || 0;
    tramo.style.width = `${(n / total) * 100}%`;
    if (n > 0 || clave !== 'Sin asignar') {
      const nombre = NOMBRE_PRIORIDAD[clave] || 'Sin asignar';
      const item = crear('li', clasePrioridad(clave), `${nombre} `);
      item.append(crear('strong', null, String(n)));
      leyenda.append(item);
      etiquetas.push(`${nombre}: ${n}`);
    }
  }
  el('reparto').setAttribute('aria-label', `Tickets por prioridad. ${etiquetas.join(', ')}.`);
}

function filtrados() {
  const prioridad = el('filtro-prioridad').value;
  const estadoFiltro = el('filtro-estado').value;
  return estado.tickets.filter((t) =>
    (!prioridad || t.prioridad === prioridad) && (!estadoFiltro || t.estado === estadoFiltro));
}

function mostrarVacio(texto) {
  el('tabla-cuerpo').replaceChildren();
  const vacio = el('vacio');
  vacio.textContent = texto;
  vacio.hidden = false;
}

function dibujarTabla() {
  const lista = filtrados();
  const cuerpo = el('tabla-cuerpo');
  cuerpo.replaceChildren();
  el('vacio').hidden = true;

  if (estado.tickets.length === 0) {
    mostrarVacio(estado.codigoBuscado
      ? `No existe el ticket ${estado.codigoBuscado}. Revisa el código o selecciona Ver todos.`
      : 'Todavía no hay tickets. Usa el formulario para registrar el primero.');
    return;
  }
  if (lista.length === 0) {
    mostrarVacio('Ningún ticket coincide con los filtros. Cambia la prioridad o el estado.');
    return;
  }

  for (const t of lista) {
    const fila = crear('tr', `fila ${clasePrioridad(t.prioridad)}`);
    if (t.codigo === estado.ultimoCodigo) fila.classList.add('reciente');

    const celdaCodigo = crear('td');
    const boton = crear('button', 'codigo-boton', t.codigo);
    boton.type = 'button';
    boton.setAttribute('aria-expanded', String(estado.abiertos.has(t.codigo)));
    boton.addEventListener('click', () => alternarDetalle(t.codigo));
    celdaCodigo.append(boton);

    const celdaPrioridad = crear('td');
    celdaPrioridad.append(crear('span', `prioridad ${clasePrioridad(t.prioridad)}`,
      NOMBRE_PRIORIDAD[t.prioridad] || 'Sin asignar'));

    const celdaEstado = crear('td');
    celdaEstado.append(crear('span', `estado estado-${String(t.estado).toLowerCase()}`,
      NOMBRE_ESTADO[t.estado] || t.estado));

    fila.append(
      celdaCodigo,
      crear('td', 'asunto', t.asunto),
      crear('td', null, t.departamento),
      crear('td', null, t.categoria || 'Pendiente'),
      celdaPrioridad,
      celdaEstado,
      crear('td', 'fecha', fechaCorta(t.fecha_creacion)),
    );
    cuerpo.append(fila);

    if (estado.abiertos.has(t.codigo)) cuerpo.append(filaDetalle(t));
  }
}

function filaDetalle(t) {
  const fila = crear('tr', 'detalle');
  const celda = crear('td');
  celda.colSpan = 7;
  const lista = crear('dl');
  const datos = [
    ['Solicitante', t.solicitante_nombre],
    ['Motivo de la clasificación', t.motivo_clasificacion || 'Todavía no se clasifica. Ocurre en menos de un minuto.'],
    ['Creado', fechaCorta(t.fecha_creacion)],
    ['Clasificado', t.fecha_clasificacion ? fechaCorta(t.fecha_clasificacion) : 'Pendiente'],
  ];
  for (const [nombre, valor] of datos) lista.append(crear('dt', null, nombre), crear('dd', null, valor));
  celda.append(lista);
  fila.append(celda);
  return fila;
}

function alternarDetalle(codigo) {
  if (estado.abiertos.has(codigo)) estado.abiertos.delete(codigo);
  else estado.abiertos.add(codigo);
  dibujarTabla();
  const boton = [...document.querySelectorAll('.codigo-boton')].find((b) => b.textContent === codigo);
  if (boton) boton.focus();
}

/* ---------------------------------------------------------------------
   Búsqueda por código
   --------------------------------------------------------------------- */
function buscar(evento) {
  evento.preventDefault();
  const valor = el('buscar-codigo').value.trim().toUpperCase();
  if (!valor) return limpiarBusqueda();
  if (!/^TCK-\d{6}$/.test(valor)) {
    mostrarVacio('Escribe el código completo, por ejemplo TCK-000001.');
    return;
  }
  estado.codigoBuscado = valor;
  el('boton-limpiar').hidden = false;
  cargarTickets();
}

function limpiarBusqueda() {
  estado.codigoBuscado = '';
  el('buscar-codigo').value = '';
  el('boton-limpiar').hidden = true;
  cargarTickets();
}

/* ---------------------------------------------------------------------
   Inicio
   --------------------------------------------------------------------- */
document.addEventListener('DOMContentLoaded', () => {
  el('formulario').addEventListener('submit', enviarTicket);
  el('descripcion').addEventListener('input', actualizarContador);
  el('boton-actualizar').addEventListener('click', cargarTickets);
  el('filtro-prioridad').addEventListener('change', dibujarTabla);
  el('filtro-estado').addEventListener('change', dibujarTabla);
  el('busqueda').addEventListener('submit', buscar);
  el('boton-limpiar').addEventListener('click', limpiarBusqueda);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) cargarTickets();
  });
  cargarTickets();
});
