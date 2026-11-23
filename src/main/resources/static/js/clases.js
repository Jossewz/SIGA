// =========================================================================
// SIGA - GESTIÓN DE CURSOS Y HORARIO ESTILO GOOGLE CALENDAR / SEGI
// =========================================================================

let vistaActualHorario = 'semana';
window.horariosActuales = [];
window.jornadaActualCurso = 'Mañana';

function handleRowClick(tr, event) {
    if (event.target.closest('button') || event.target.closest('form')) return;
    if (window.esSoloEstudiante) {
        if (tr.dataset.id) {
            openHorarioModal(tr.dataset.id, tr.dataset.codigo, tr.dataset.jornada);
        }
        return;
    }
    window.location.href = '/clases/gestion?codigo=' + encodeURIComponent(tr.dataset.codigo);
}

function handleHorarioBtn(btn, event) {
    if (event) event.stopPropagation();
    openHorarioModal(btn.dataset.id, btn.dataset.codigo, btn.dataset.jornada);
}

function normalizarDia(dia) {
    if (!dia) return '';
    return dia.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
}

function getMateriasList() {
    if (window.materiasGlobales && Array.isArray(window.materiasGlobales) && window.materiasGlobales.length > 0) {
        return window.materiasGlobales;
    }
    const selectMat = document.getElementById('fc-materia');
    if (selectMat) {
        const list = [];
        Array.from(selectMat.options).forEach(opt => {
            if (opt.value) list.push({ id: opt.value, nombre: opt.text });
        });
        if (list.length > 0) return list;
    }
    return [];
}

function getDocentesList() {
    if (window.docentesGlobales && Array.isArray(window.docentesGlobales) && window.docentesGlobales.length > 0) {
        return window.docentesGlobales;
    }
    const selectDoc = document.getElementById('fc-docente');
    if (selectDoc) {
        const list = [];
        Array.from(selectDoc.options).forEach(opt => {
            if (opt.value) list.push({ id: opt.value, nombreCompleto: opt.text });
        });
        if (list.length > 0) return list;
    }
    return [];
}

// -------------------------------------------------------------------------
// MODAL HORARIO (GOOGLE CALENDAR / SEGI STYLE)
// -------------------------------------------------------------------------
async function openHorarioModal(cursoId, codigo, jornada) {
    window.jornadaActualCurso = (jornada && jornada.trim()) ? jornada.trim() : 'Mañana';

    const titleElem = document.getElementById('horario-title');
    if (titleElem) titleElem.innerText = 'Horario Semanal — ' + (codigo ? 'Curso ' + codigo : 'Curso');

    const subtituloElem = document.getElementById('horario-subtitulo');
    if (subtituloElem) {
        const esTarde = window.jornadaActualCurso.toLowerCase().includes('tar');
        subtituloElem.innerText = esTarde
            ? 'Jornada Tarde (13:00 – 18:30) • Gestión flexible de franjas horarias, materias, docentes y aulas.'
            : 'Jornada Mañana (07:00 – 12:30) • Gestión flexible de franjas horarias, materias, docentes y aulas.';
    }

    const cursoIdInput = document.getElementById('h-curso-id');
    if (cursoIdInput) cursoIdInput.value = cursoId || '';

    const modal = document.getElementById('horario-modal');
    const dialog = document.getElementById('horario-modal-dialog');
    const loading = document.getElementById('horario-loading');
    const semanaContainer = document.getElementById('horario-semana-container');
    const agendaContainer = document.getElementById('horario-agenda-container');

    if (loading) loading.classList.remove('hidden');
    if (semanaContainer) semanaContainer.classList.add('hidden');
    if (agendaContainer) agendaContainer.classList.add('hidden');
    const modeloIoContainer = document.getElementById('horario-modelo-io-container');
    if (modeloIoContainer) modeloIoContainer.classList.add('hidden');
    const modalForm = document.getElementById('modal-form-clase');
    if (modalForm) modalForm.classList.add('hidden');
    if (dialog) dialog.classList.remove('hidden');

    modal.classList.remove('hidden');
    setTimeout(() => {
        dialog.classList.remove('scale-95', 'opacity-0');
        dialog.classList.add('scale-100', 'opacity-100');
    }, 10);

    vistaActualHorario = 'semana';
    actualizarBotonesVista();

    try {
        const resp = await fetch('/clases/horarios/datos?cursoId=' + encodeURIComponent(cursoId));
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const horarios = await resp.json();
        window.horariosActuales = Array.isArray(horarios) ? horarios : [];
        renderHorarioCompleto();
    } catch (err) {
        console.error('Error al cargar datos del horario:', err);
        if (loading) {
            loading.innerHTML = `<span class="text-alert-red font-bold">Error al cargar horarios: ${err.message}</span>`;
        }
    }
}

function closeHorarioModal() {
    const modal = document.getElementById('horario-modal');
    const dialog = document.getElementById('horario-modal-dialog');
    dialog.classList.remove('scale-100', 'opacity-100');
    dialog.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        modal.classList.add('hidden');
        cerrarModalFormClase();
    }, 250);
}

function cambiarVistaHorario(vista) {
    vistaActualHorario = vista;
    actualizarBotonesVista();
    renderHorarioCompleto();
}

function actualizarBotonesVista() {
    const btnSemana = document.getElementById('btn-vista-semana');
    const btnAgenda = document.getElementById('btn-vista-agenda');
    const btnModelo = document.getElementById('btn-vista-modelo-io');
    if (!btnSemana || !btnAgenda) return;

    const activeCls = "inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-black transition-all bg-sidebar text-white shadow-2xs cursor-pointer";
    const inactiveCls = "inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-black transition-all text-text-secondary hover:text-sidebar cursor-pointer";

    btnSemana.className = vistaActualHorario === 'semana' ? activeCls : inactiveCls;
    btnAgenda.className = vistaActualHorario === 'agenda' ? activeCls : inactiveCls;
    if (btnModelo) {
        btnModelo.className = vistaActualHorario === 'modelo-io' ? activeCls : inactiveCls;
    }
}

function calcularHorasTotales(horarios) {
    let minutosTotales = 0;
    horarios.forEach(h => {
        if (!h.horaInicio || !h.horaFin) return;
        const [h1, m1] = h.horaInicio.split(':').map(Number);
        const [h2, m2] = h.horaFin.split(':').map(Number);
        const min1 = h1 * 60 + m1;
        const min2 = h2 * 60 + m2;
        if (min2 > min1) {
            minutosTotales += (min2 - min1);
        }
    });
    const horas = Math.floor(minutosTotales / 60);
    const mins = minutosTotales % 60;
    if (mins === 0) return `${horas} horas lectivas semanales`;
    return `${horas}h ${mins}m semanales`;
}

function renderHorarioCompleto() {
    const loading = document.getElementById('horario-loading');
    const semanaContainer = document.getElementById('horario-semana-container');
    const agendaContainer = document.getElementById('horario-agenda-container');
    const modeloIoContainer = document.getElementById('horario-modelo-io-container');
    const badgeConteo = document.getElementById('horario-conteo-badge');
    const labelHorasTotales = document.getElementById('horario-horas-totales');
    const labelMateriasTotales = document.getElementById('horario-materias-totales');

    if (loading) loading.classList.add('hidden');

    const totalClases = window.horariosActuales.length;
    if (badgeConteo) {
        badgeConteo.innerText = `${totalClases} ${totalClases === 1 ? 'clase' : 'clases'}`;
    }

    if (labelHorasTotales) {
        const textoHoras = calcularHorasTotales(window.horariosActuales);
        labelHorasTotales.innerHTML = `<i data-lucide="clock" class="size-4 stroke-[2.5]"></i><span>${textoHoras}</span>`;
    }

    const materiasSet = new Set(window.horariosActuales.map(h => h.materiaNombre).filter(Boolean));
    if (labelMateriasTotales) {
        labelMateriasTotales.innerText = `${materiasSet.size} asignaturas distintas`;
    }

    if (vistaActualHorario === 'semana') {
        if (semanaContainer) semanaContainer.classList.remove('hidden');
        if (agendaContainer) agendaContainer.classList.add('hidden');
        if (modeloIoContainer) modeloIoContainer.classList.add('hidden');
        renderVistaSemanalGoogleCalendar();
    } else if (vistaActualHorario === 'agenda') {
        if (agendaContainer) agendaContainer.classList.remove('hidden');
        if (semanaContainer) semanaContainer.classList.add('hidden');
        if (modeloIoContainer) modeloIoContainer.classList.add('hidden');
        renderVistaAgendaSEGI();
    } else if (vistaActualHorario === 'modelo-io') {
        if (modeloIoContainer) modeloIoContainer.classList.remove('hidden');
        if (semanaContainer) semanaContainer.classList.add('hidden');
        if (agendaContainer) agendaContainer.classList.add('hidden');
        cargarYRenderizarModeloIO();
    }

    if (window.lucide) lucide.createIcons();
}

// -------------------------------------------------------------------------
// VISTA SEMANAL TIPO GOOGLE CALENDAR
// -------------------------------------------------------------------------
function renderVistaSemanalGoogleCalendar() {
    const dias = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];

    dias.forEach(dia => {
        const colContainer = document.getElementById(`cards-${dia}`);
        const diaCol = document.querySelector(`.dia-col[data-dia="${dia}"]`);
        if (!colContainer) return;

        colContainer.innerHTML = '';

        const clasesDelDia = window.horariosActuales.filter(h => {
            return normalizarDia(h.dia) === normalizarDia(dia);
        });

        // Ordenar cronológicamente por hora de inicio
        clasesDelDia.sort((a, b) => (a.horaInicio || '').localeCompare(b.horaInicio || ''));

        if (diaCol) {
            const countSpan = diaCol.querySelector('.dia-count');
            if (countSpan) countSpan.innerText = clasesDelDia.length;
        }

        if (clasesDelDia.length === 0) {
            colContainer.innerHTML = `
                <div class="h-32 border-2 border-dashed border-[#c4eec0]/50 rounded-xl flex flex-col items-center justify-center p-3 text-center text-[#9db89a] text-[11px] font-semibold">
                    <i data-lucide="calendar" class="size-5 mb-1 opacity-50"></i>
                    <span>Sin clases asignadas</span>
                </div>
            `;
            return;
        }

        clasesDelDia.forEach(c => {
            const card = document.createElement('div');
            const color = c.color || '#0d4117';
            const puedeEditar = Boolean(window.puedeAdministrarCursos);
            card.className = "group relative bg-white border border-[#c4eec0] hover:border-sidebar/60 rounded-xl p-2.5 shadow-2xs hover:shadow-md transition-all flex flex-col gap-1.5 " + (puedeEditar ? "cursor-pointer" : "");
            card.style.borderLeftWidth = "4px";
            card.style.borderLeftColor = color;

            const horaFormat = `${formatearHora12(c.horaInicio)} - ${formatearHora12(c.horaFin)}`;
            const accionesHtml = puedeEditar ? `
                    <div class="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1 shrink-0">
                        <button type="button" onclick="event.stopPropagation(); abrirModalEditarClase('${c.id}')"
                                class="p-1 hover:bg-[#eafbe4] text-sidebar rounded-md transition-colors" title="Editar horario">
                            <i data-lucide="pencil" class="size-3 stroke-[2.2]"></i>
                        </button>
                        <button type="button" onclick="event.stopPropagation(); eliminarClaseItem('${c.id}')"
                                class="p-1 hover:bg-red-50 text-alert-red rounded-md transition-colors" title="Eliminar clase">
                            <i data-lucide="trash-2" class="size-3 stroke-[2.2]"></i>
                        </button>
                    </div>` : '';

            card.innerHTML = `
                <div class="flex items-start justify-between gap-1">
                    <span class="text-[12px] font-black text-sidebar leading-tight line-clamp-2">
                        ${c.materiaNombre || 'Sin Asignatura'}
                    </span>
                    ${accionesHtml}
                </div>

                <div class="flex items-center gap-1.5 text-[10px] font-bold text-text-secondary">
                    <i data-lucide="clock-3" class="size-3 stroke-2 text-sidebar"></i>
                    <span class="tabular-nums font-black text-brand-900">${horaFormat}</span>
                </div>

                <div class="flex items-center justify-between gap-1 pt-1 border-t border-[#f0f9ef]">
                    <span class="text-[10px] font-semibold text-text-secondary truncate flex items-center gap-1 min-w-0" title="${c.docenteNombre || 'Sin Docente'}">
                        <i data-lucide="user" class="size-3 stroke-2 text-text-muted shrink-0"></i>
                        <span class="truncate">${c.docenteNombre ? c.docenteNombre.split(' ')[0] : 'Sin Docente'}</span>
                    </span>
                    <span class="px-1.5 py-0.5 rounded bg-[#eafbe4] text-sidebar border border-[#c4eec0] text-[9px] font-black uppercase tracking-wider truncate max-w-[125px]" title="${c.salon || 'Aula 101'}">
                        ${c.salon || 'Aula 101'}
                    </span>
                </div>
            `;

            if (puedeEditar) {
                card.onclick = () => abrirModalEditarClase(c.id);
            }
            colContainer.appendChild(card);
        });
    });
}

// -------------------------------------------------------------------------
// VISTA AGENDA TIPO SEGI (LISTA CRONOLÓGICA)
// -------------------------------------------------------------------------
function renderVistaAgendaSEGI() {
    const container = document.getElementById('horario-agenda-container');
    if (!container) return;
    container.innerHTML = '';

    const dias = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];

        if (window.horariosActuales.length === 0) {
        container.innerHTML = `
            <div class="p-12 text-center text-text-secondary font-bold text-xs bg-[#f7fcf6] border border-[#c4eec0] rounded-2xl">
                <i data-lucide="calendar-x" class="size-8 mx-auto text-[#a5cca0] mb-2"></i>
                <p class="text-sidebar font-black text-sm mb-1">No hay clases programadas</p>
                <p>${window.puedeAdministrarCursos ? 'Haga clic en "+ Programar Clase" para comenzar a estructurar el horario del curso.' : 'Este curso aún no tiene clases programadas en el horario.'}</p>
            </div>
        `;
        return;
    }

    dias.forEach(dia => {
        const clasesDelDia = window.horariosActuales.filter(h => {
            return normalizarDia(h.dia) === normalizarDia(dia);
        });

        if (clasesDelDia.length === 0) return;

        clasesDelDia.sort((a, b) => (a.horaInicio || '').localeCompare(b.horaInicio || ''));

        const bloqueDia = document.createElement('div');
        bloqueDia.className = "bg-white border border-[#c4eec0] rounded-2xl overflow-hidden shadow-2xs";

        let tarjetasHtml = '';
        const puedeEditar = Boolean(window.puedeAdministrarCursos);
        clasesDelDia.forEach(c => {
            const color = c.color || '#0d4117';
            const horaFormat = `${formatearHora12(c.horaInicio)} — ${formatearHora12(c.horaFin)}`;
            const botonesAgenda = puedeEditar ? `
                    <div class="flex items-center gap-1.5 shrink-0 opacity-80 group-hover:opacity-100 transition-opacity">
                        <button type="button" onclick="abrirModalEditarClase('${c.id}')"
                                class="px-3 py-1.5 rounded-lg border border-[#c4eec0] bg-white hover:bg-[#eafbe4] text-sidebar text-xs font-bold transition-all flex items-center gap-1">
                            <i data-lucide="pencil" class="size-3 stroke-[2.2]"></i>
                            <span>Editar</span>
                        </button>
                        <button type="button" onclick="eliminarClaseItem('${c.id}')"
                                class="px-2.5 py-1.5 rounded-lg border border-red-200 bg-white hover:bg-red-50 text-alert-red text-xs font-bold transition-all" title="Eliminar clase">
                            <i data-lucide="trash-2" class="size-3.5 stroke-[2.2]"></i>
                        </button>
                    </div>` : '';

            tarjetasHtml += `
                <div class="flex items-center justify-between p-3.5 hover:bg-[#f7fcf6] transition-colors border-b last:border-b-0 border-[#c4eec0]/40 group">
                    <div class="flex items-center gap-3.5 min-w-0">
                        <span class="w-3 h-3 rounded-full shrink-0 ring-2 ring-white shadow-2xs" style="background-color: ${color}"></span>
                        <div class="flex flex-col">
                            <div class="flex items-center gap-2">
                                <span class="px-2 py-0.5 rounded-lg bg-[#eafbe4] text-sidebar border border-[#c4eec0] text-[11px] font-black tabular-nums">
                                    ${horaFormat}
                                </span>
                                <strong class="text-sm font-black text-sidebar truncate">${c.materiaNombre || 'Sin Asignatura'}</strong>
                            </div>
                            <div class="flex items-center gap-3 text-xs font-semibold text-text-secondary mt-1">
                                <span class="flex items-center gap-1">
                                    <i data-lucide="user" class="size-3.5 stroke-2"></i>
                                    <span>${c.docenteNombre || 'Sin docente asignado'}</span>
                                </span>
                                <span>•</span>
                                <span class="flex items-center gap-1 text-sidebar font-bold">
                                    <i data-lucide="map-pin" class="size-3.5 stroke-2"></i>
                                    <span>${c.salon || 'Aula 101'}</span>
                                </span>
                            </div>
                        </div>
                    </div>
                    ${botonesAgenda}
                </div>
            `;
        });

        bloqueDia.innerHTML = `
            <div class="px-5 py-3 bg-[#eafbe4]/60 border-b border-[#c4eec0] flex items-center justify-between">
                <div class="flex items-center gap-2">
                    <span class="w-3 h-3 rounded-full bg-sidebar"></span>
                    <h4 class="text-sidebar text-sm font-black uppercase tracking-wider">${dia}</h4>
                </div>
                <span class="text-xs font-black text-sidebar">${clasesDelDia.length} ${clasesDelDia.length === 1 ? 'clase' : 'clases'}</span>
            </div>
            <div class="divide-y divide-[#c4eec0]/40">
                ${tarjetasHtml}
            </div>
        `;

        container.appendChild(bloqueDia);
    });
}

// -------------------------------------------------------------------------
// FORMULARIO DINÁMICO DE CREAR / EDITAR CLASE (HORAS TOTALMENTE FLEXIBLES)
// -------------------------------------------------------------------------
function obtenerPresetsPorJornada(jornada) {
    const esTarde = (jornada || '').toLowerCase().includes('tar');
    if (esTarde) {
        return {
            etiqueta: 'Jornada Tarde (1:00 PM - 6:30 PM • Descanso 3:00 - 3:30 PM)',
            inicioDefecto: '13:00',
            finDefecto: '14:00',
            presets: [
                { inicio: '13:00', fin: '14:00' },
                { inicio: '14:00', fin: '15:00' },
                { inicio: '15:30', fin: '16:30' },
                { inicio: '16:30', fin: '17:30' },
                { inicio: '17:30', fin: '18:30' },
                { inicio: '15:30', fin: '17:30' }
            ]
        };
    }
    return {
        etiqueta: 'Jornada Mañana (7:00 AM - 12:30 PM • Descanso 9:30 - 10:00 AM)',
        inicioDefecto: '07:00',
        finDefecto: '08:00',
        presets: [
            { inicio: '07:00', fin: '08:00' },
            { inicio: '08:00', fin: '09:30' },
            { inicio: '10:00', fin: '11:00' },
            { inicio: '11:00', fin: '12:30' },
            { inicio: '07:00', fin: '08:30' },
            { inicio: '10:00', fin: '12:00' }
        ]
    };
}

function renderizarPresetsJornada() {
    const cfg = obtenerPresetsPorJornada(window.jornadaActualCurso);
    const badge = document.getElementById('fc-jornada-badge');
    const container = document.getElementById('fc-presets-container');

    if (badge) badge.textContent = cfg.etiqueta;
    if (container) {
        container.innerHTML = '';
        cfg.presets.forEach(p => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'px-2.5 py-1 bg-white hover:bg-[#eafbe4] border border-[#c4eec0] rounded-lg text-[10px] font-extrabold text-sidebar transition-colors cursor-pointer';
            btn.textContent = `${p.inicio} - ${p.fin}`;
            btn.onclick = () => aplicarPresetHoras(p.inicio, p.fin);
            container.appendChild(btn);
        });
    }
    return cfg;
}

function abrirModalCrearClase(diaSugerido) {
    const modalForm = document.getElementById('modal-form-clase');
    const formTitle = document.getElementById('form-clase-titulo');
    const btnText = document.getElementById('fc-btn-text');

    if (formTitle) formTitle.innerText = "Programar Nueva Clase";
    if (btnText) btnText.innerText = "Guardar Clase";

    const cfg = renderizarPresetsJornada();

    const idInput = document.getElementById('fc-horario-id');
    const diaSelect = document.getElementById('fc-dia');
    const horaInicioInput = document.getElementById('fc-hora-inicio');
    const horaFinInput = document.getElementById('fc-hora-fin');
    const materiaSelect = document.getElementById('fc-materia');
    const docenteSelect = document.getElementById('fc-docente');
    const salonSelect = document.getElementById('fc-salon');
    const salonCustom = document.getElementById('fc-salon-custom');

    if (idInput) idInput.value = '';
    if (diaSelect && diaSugerido) diaSelect.value = diaSugerido;
    if (horaInicioInput) horaInicioInput.value = cfg.inicioDefecto;
    if (horaFinInput) horaFinInput.value = cfg.finDefecto;
    if (materiaSelect) materiaSelect.selectedIndex = 0;
    if (docenteSelect) docenteSelect.selectedIndex = 0;
    if (salonSelect && salonSelect.options.length > 0) salonSelect.selectedIndex = 0;
    if (salonCustom) salonCustom.value = '';

    const horarioDialog = document.getElementById('horario-modal-dialog');
    if (horarioDialog) horarioDialog.classList.add('hidden');
    if (modalForm) modalForm.classList.remove('hidden');
    if (window.lucide) lucide.createIcons();
}

function abrirModalEditarClase(horarioId) {
    const clase = window.horariosActuales.find(h => String(h.id) === String(horarioId));
    if (!clase) return;

    const modalForm = document.getElementById('modal-form-clase');
    const formTitle = document.getElementById('form-clase-titulo');
    const btnText = document.getElementById('fc-btn-text');

    if (formTitle) formTitle.innerText = "Editar Clase";
    if (btnText) btnText.innerText = "Actualizar Cambios";

    const cfg = renderizarPresetsJornada();

    const idInput = document.getElementById('fc-horario-id');
    const diaSelect = document.getElementById('fc-dia');
    const horaInicioInput = document.getElementById('fc-hora-inicio');
    const horaFinInput = document.getElementById('fc-hora-fin');
    const materiaSelect = document.getElementById('fc-materia');
    const docenteSelect = document.getElementById('fc-docente');
    const salonSelect = document.getElementById('fc-salon');
    const salonCustom = document.getElementById('fc-salon-custom');

    if (idInput) idInput.value = clase.id;
    if (diaSelect) diaSelect.value = clase.dia;
    if (horaInicioInput) horaInicioInput.value = (clase.horaInicio || cfg.inicioDefecto).substring(0, 5);
    if (horaFinInput) horaFinInput.value = (clase.horaFin || cfg.finDefecto).substring(0, 5);
    if (materiaSelect) materiaSelect.value = clase.materiaId || '';
    if (docenteSelect) docenteSelect.value = clase.docenteId || '';
    
    if (salonSelect) {
        salonSelect.value = clase.salon || (salonSelect.options[0]?.value || 'Aula 101');
    }
    if (salonCustom) {
        salonCustom.value = clase.salon || '';
    }

    const horarioDialog = document.getElementById('horario-modal-dialog');
    if (horarioDialog) horarioDialog.classList.add('hidden');
    if (modalForm) modalForm.classList.remove('hidden');
    if (window.lucide) lucide.createIcons();
}

function cerrarModalFormClase() {
    const modalForm = document.getElementById('modal-form-clase');
    const horarioDialog = document.getElementById('horario-modal-dialog');
    if (modalForm) modalForm.classList.add('hidden');
    if (horarioDialog) horarioDialog.classList.remove('hidden');
}

function aplicarPresetHoras(inicio, fin) {
    const horaInicioInput = document.getElementById('fc-hora-inicio');
    const horaFinInput = document.getElementById('fc-hora-fin');
    if (horaInicioInput) horaInicioInput.value = inicio;
    if (horaFinInput) horaFinInput.value = fin;
}

function sincronizarSalonInput(val) {
    const salonCustom = document.getElementById('fc-salon-custom');
    if (salonCustom) salonCustom.value = val;
}

function obtenerCsrfInfo() {
    const metaToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const metaHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';
    const formInput = document.getElementById('fc-csrf-input')?.value;
    
    // Cookie fallback
    let cookieVal = null;
    const cookieMatch = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    if (cookieMatch) {
        cookieVal = decodeURIComponent(cookieMatch[1]);
    }
    
    const token = metaToken || formInput || cookieVal || '';
    return { token, header: metaHeader };
}

async function guardarClaseItem(event) {
    event.preventDefault();

    const cursoId = document.getElementById('h-curso-id')?.value;
    const horarioId = document.getElementById('fc-horario-id')?.value;
    const dia = document.getElementById('fc-dia')?.value;
    const horaInicio = document.getElementById('fc-hora-inicio')?.value;
    const horaFin = document.getElementById('fc-hora-fin')?.value;
    const materiaId = document.getElementById('fc-materia')?.value;
    const docenteId = document.getElementById('fc-docente')?.value;
    const salonCustom = document.getElementById('fc-salon-custom')?.value;
    const salonSelect = document.getElementById('fc-salon')?.value;
    const salon = (salonCustom && salonCustom.trim()) ? salonCustom.trim() : (salonSelect || 'Aula 101');

    const mostrarAlerta = (msg, tipo = 'error') => {
        if (typeof window.sigaAlert === 'function') window.sigaAlert(msg, tipo);
        else alert(msg);
    };

    if (!cursoId) {
        mostrarAlerta('Error: No se ha identificado el curso.');
        return;
    }
    if (!materiaId) {
        mostrarAlerta('Por favor seleccione una materia.', 'warning');
        return;
    }
    if (!horaInicio || !horaFin) {
        mostrarAlerta('Por favor especifique la hora de inicio y de fin.', 'warning');
        return;
    }
    if (horaInicio >= horaFin) {
        mostrarAlerta('La hora de inicio debe ser anterior a la hora de fin.', 'warning');
        return;
    }

    const csrf = obtenerCsrfInfo();
    const formData = new URLSearchParams();
    formData.append('cursoId', cursoId);
    formData.append('diaSemana', dia);
    if (horarioId) formData.append('horarioId', horarioId);
    formData.append('materiaId', materiaId);
    if (docenteId) formData.append('docenteId', docenteId);
    formData.append('horaInicio', horaInicio);
    formData.append('horaFin', horaFin);
    formData.append('salon', salon);
    if (csrf.token) {
        formData.append('_csrf', csrf.token);
    }

    const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (csrf.token) {
        headers[csrf.header] = csrf.token;
    }

    const submitBtn = document.getElementById('fc-btn-submit');
    if (submitBtn) submitBtn.disabled = true;

    try {
        const resp = await fetch('/clases/horarios/guardar-item', {
            method: 'POST',
            headers: headers,
            body: formData.toString()
        });

        const data = await resp.json();
        if (!data.success) {
            throw new Error(data.error || 'Error al guardar la clase');
        }

        // Recargar datos actualizados
        const refreshResp = await fetch('/clases/horarios/datos?cursoId=' + encodeURIComponent(cursoId));
        if (refreshResp.ok) {
            window.horariosActuales = await refreshResp.json();
        }

        cerrarModalFormClase();
        renderHorarioCompleto();

        // Actualizar botón de horario en la tabla principal si existe
        const tableBtn = document.querySelector(`button[data-id="${cursoId}"]`);
        if (tableBtn) {
            tableBtn.className = "px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wide leading-none transition-all inline-flex items-center gap-1.5 cursor-pointer shadow-2xs bg-pill-green text-sidebar border border-[#8ce383] hover:brightness-95";
            tableBtn.querySelector('span').innerText = 'Asignado';
        }
    } catch (err) {
        const msg = 'Error al guardar la clase: ' + err.message;
        if (typeof window.sigaAlert === 'function') window.sigaAlert(msg, 'error'); else alert(msg);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function eliminarClaseItem(horarioId) {
    if (!confirm('¿Está seguro de eliminar esta clase del horario?')) return;

    const cursoId = document.getElementById('h-curso-id')?.value;
    const csrf = obtenerCsrfInfo();
    const formData = new URLSearchParams();
    formData.append('horarioId', horarioId);
    if (csrf.token) {
        formData.append('_csrf', csrf.token);
    }

    const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (csrf.token) {
        headers[csrf.header] = csrf.token;
    }

    try {
        const resp = await fetch('/clases/horarios/eliminar-item', {
            method: 'POST',
            headers: headers,
            body: formData.toString()
        });
        const data = await resp.json();
        if (!data.success) {
            throw new Error(data.error || 'Error al eliminar');
        }

        window.horariosActuales = window.horariosActuales.filter(h => String(h.id) !== String(horarioId));
        renderHorarioCompleto();

        if (cursoId && window.horariosActuales.length === 0) {
            const tableBtn = document.querySelector(`button[data-id="${cursoId}"]`);
            if (tableBtn) {
                tableBtn.className = "px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wide leading-none transition-all inline-flex items-center gap-1.5 cursor-pointer shadow-2xs bg-amber-100 text-amber-800 border border-amber-300 hover:bg-amber-200";
                tableBtn.querySelector('span').innerText = 'Sin Horario';
            }
        }
    } catch (err) {
        const msg = 'Error al eliminar la clase: ' + err.message;
        if (typeof window.sigaAlert === 'function') window.sigaAlert(msg, 'error'); else alert(msg);
    }
}

async function confirmarLimpiarHorario() {
    const cursoId = document.getElementById('h-curso-id')?.value;
    if (!cursoId) return;

    if (!confirm('¿Está seguro de que desea eliminar todas las clases asignadas a este curso? Esta acción no se puede deshacer.')) {
        return;
    }

    const csrf = obtenerCsrfInfo();
    const formData = new URLSearchParams();
    formData.append('cursoId', cursoId);
    if (csrf.token) {
        formData.append('_csrf', csrf.token);
    }

    const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (csrf.token) {
        headers[csrf.header] = csrf.token;
    }

    try {
        const resp = await fetch('/clases/horarios/limpiar', {
            method: 'POST',
            headers: headers,
            body: formData.toString()
        });
        const data = await resp.json();
        if (!data.success) throw new Error(data.error || 'Error al limpiar horario');

        window.horariosActuales = [];
        renderHorarioCompleto();

        const tableBtn = document.querySelector(`button[data-id="${cursoId}"]`);
        if (tableBtn) {
            tableBtn.className = "px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wide leading-none transition-all inline-flex items-center gap-1.5 cursor-pointer shadow-2xs bg-amber-100 text-amber-800 border border-amber-300 hover:bg-amber-200";
            tableBtn.querySelector('span').innerText = 'Sin Horario';
        }
    } catch (err) {
        const msg = 'Error al limpiar el horario: ' + err.message;
        if (typeof window.sigaAlert === 'function') window.sigaAlert(msg, 'error'); else alert(msg);
    }
}

function formatearHora12(horaStr) {
    if (!horaStr) return '07:00';
    try {
        const [h, m] = horaStr.split(':').map(Number);
        const ampm = h >= 12 ? 'PM' : 'AM';
        const h12 = h % 12 || 12;
        return `${String(h12).padStart(2, '0')}:${String(m).padStart(2, '0')} ${ampm}`;
    } catch (e) {
        return horaStr;
    }
}

// -------------------------------------------------------------------------
// MODAL DE GESTIÓN Y CURSOS
// -------------------------------------------------------------------------
function handleGestionBtn(btn, event) {
    event.stopPropagation();
    window.location.href = '/clases/gestion?codigo=' + encodeURIComponent(btn.dataset.codigo);
}

function handleEditarCursoBtn(btn, event) {
    if (event) event.stopPropagation();
    const data = {
        id: btn.dataset.id,
        codigoCurso: btn.dataset.codigo,
        grado: btn.dataset.grado,
        jornada: btn.dataset.jornada,
        cupos: btn.dataset.cupos,
        directorId: btn.dataset.directorid
    };
    openCursoModal(data);
}

function openCursoModal(data) {
    const modal = document.getElementById('curso-modal');
    const dialog = document.getElementById('curso-modal-dialog');
    const title = document.getElementById('modal-curso-title');
    const btnText = document.getElementById('modal-curso-btn-text');

    const idInput = document.getElementById('c-id');
    const gradeSelect = document.getElementById('c-grade');
    const jornadaSelect = document.getElementById('c-jornada');
    const directorSelect = document.getElementById('c-director');
    const cuposInput = document.getElementById('c-cupos');

    if (data) {
        if (title) title.innerText = 'Editar Curso ' + (data.codigoCurso || '');
        if (btnText) btnText.innerText = 'Guardar Cambios';
        if (idInput) idInput.value = data.id || '';
        if (gradeSelect) gradeSelect.value = data.grado || '11°';
        if (jornadaSelect) jornadaSelect.value = data.jornada || 'Mañana';
        if (directorSelect) directorSelect.value = data.directorId || '';
        if (cuposInput) cuposInput.value = data.cupos || 35;
    } else {
        if (title) title.innerText = 'Crear Nuevo Curso';
        if (btnText) btnText.innerText = 'Crear Curso';
        if (idInput) idInput.value = '';
        const form = document.getElementById('form-register-curso');
        if (form) form.reset();
    }

    modal.classList.remove('hidden');
    setTimeout(() => {
        dialog.classList.remove('scale-95', 'opacity-0');
        dialog.classList.add('scale-100', 'opacity-100');
    }, 10);
}

function closeCursoModal() {
    const modal = document.getElementById('curso-modal');
    const dialog = document.getElementById('curso-modal-dialog');
    dialog.classList.remove('scale-100', 'opacity-100');
    dialog.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        modal.classList.add('hidden');
    }, 250);
}

// -------------------------------------------------------------------------
// RECOMENDACIÓN Y GENERACIÓN AUTOMÁTICA DE HORARIO PARA EL CURSO
// -------------------------------------------------------------------------
async function optimizarHorarioCursoIO() {
    const cursoId = document.getElementById('h-curso-id')?.value;
    if (!cursoId) {
        alert('No se ha identificado el curso.');
        return;
    }

    const btn = document.getElementById('btn-optimizar-horario-io');
    const originalHtml = btn ? btn.innerHTML : '';
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<i data-lucide="loader-2" class="size-4 animate-spin"></i><span>Organizando horario...</span>`;
        if (window.lucide) lucide.createIcons();
    }

    const csrf = obtenerCsrfInfo();
    const formData = new URLSearchParams();
    formData.append('cursoId', cursoId);
    formData.append('reemplazar', 'true');
    if (csrf.token) formData.append('_csrf', csrf.token);

    const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (csrf.token) headers[csrf.header] = csrf.token;

    try {
        const resp = await fetch('/clases/horarios/optimizar', {
            method: 'POST',
            headers: headers,
            body: formData.toString()
        });
        const data = await resp.json();
        if (!resp.ok || !data.success) {
            throw new Error(data.error || `HTTP ${resp.status}`);
        }

        window.horariosActuales = Array.isArray(data.horarios) ? data.horarios : [];
        window.ultimoResultadoOptimizacionIO = data;

        const tableBtn = document.querySelector(`button[data-id="${cursoId}"]`);
        if (tableBtn) {
            tableBtn.className = "px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-wide leading-none transition-all inline-flex items-center gap-1.5 cursor-pointer shadow-2xs bg-pill-green text-sidebar border border-[#8ce383] hover:brightness-95";
            const sp = tableBtn.querySelector('span');
            if (sp) sp.innerText = 'Asignado';
        }

        renderHorarioCompleto();
    } catch (err) {
        alert('Error al organizar el horario: ' + err.message);
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = originalHtml;
            if (window.lucide) lucide.createIcons();
        }
    }
}

async function cargarYRenderizarModeloIO() {
    const container = document.getElementById('horario-modelo-io-container');
    const cursoId = document.getElementById('h-curso-id')?.value;
    if (!container || !cursoId) return;

    container.innerHTML = `
        <div class="p-10 bg-[#f7fcf6] border border-[#c4eec0] rounded-2xl text-center">
            <div class="size-10 rounded-full border-4 border-[#c4eec0] border-t-sidebar animate-spin mx-auto mb-3"></div>
            <p class="text-xs font-black text-sidebar">Analizando el horario actual del curso y preparando recomendaciones...</p>
        </div>
    `;

    try {
        const resp = await fetch('/clases/horarios/modelo-io?cursoId=' + encodeURIComponent(cursoId));
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const data = await resp.json();
        container.innerHTML = renderPanelModeloIO(data);
        inicializarEvaluadorModeloIO();
        if (window.lucide) lucide.createIcons();
    } catch (err) {
        container.innerHTML = `
            <div class="p-6 bg-red-50 border border-red-200 rounded-2xl text-xs font-bold text-alert-red">
                No fue posible cargar la recomendación de horario: ${err.message}
            </div>
        `;
    }
}

function renderPanelModeloIO(data) {
    const rt = data.datosReales || {};
    const opt = data.optimo || {};
    const params = opt.parametros || { hTopeSemanal: 30, lCargaMinima: 20, mMinimoTeorico: 12, pMinimoPractico: 6 };
    const ranking = data.ranking || [];
    const est = data.estructura || {};
    const planGrado = Array.isArray(est.planGrado) ? est.planGrado : [];

    const clasesDiariasTexto = (est.clasesPorDiaMin || 0) === (est.clasesPorDiaMax || 0)
        ? `${est.clasesPorDiaMin || 0} clases por día`
        : `${est.clasesPorDiaMin || 0} a ${est.clasesPorDiaMax || 0} clases por día`;

    const cumpleTodo = Boolean(rt.cumpleRegionFactible) && Boolean(est.cumpleEstructuraDiaria);
    const badgeEstadoReal = cumpleTodo
        ? `<span class="px-2.5 py-1 rounded-full bg-pill-green text-sidebar border border-[#8ce383] text-[10px] font-black uppercase">✓ Horario equilibrado (${clasesDiariasTexto})</span>`
        : (rt.cumpleRegionFactible
            ? `<span class="px-2.5 py-1 rounded-full bg-amber-100 text-amber-800 border border-amber-300 text-[10px] font-black uppercase">Organizable en 3 a 5 clases por día con descanso</span>`
            : `<span class="px-2.5 py-1 rounded-full bg-red-100 text-alert-red border border-red-300 text-[10px] font-black uppercase">Horario incompleto • ${rt.totalHorasReal || 0} de ${params.lCargaMinima} horas mínimas</span>`);

    const totalPlanGrado = est.totalHorasPlanGrado || opt.totalHorasOptimo || 22;

    return `
        <!-- 1. RESUMEN PRINCIPAL EN LENGUAJE CLARO -->
        <div class="bg-gradient-to-r from-[#f2fbf0] via-white to-[#f2fbf0] border border-[#c4eec0] rounded-2xl p-5 shadow-2xs">
            <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div class="space-y-1.5">
                    <div class="flex items-center gap-2 flex-wrap">
                        <span class="px-2.5 py-0.5 rounded-full bg-sidebar text-white text-[10px] font-black uppercase tracking-wider">Distribución Recomendada</span>
                        ${badgeEstadoReal}
                    </div>
                    <h4 class="text-base font-black text-text-primary">Recomendación de Horario Semanal — Curso ${rt.codigoCurso || ''}</h4>
                    <p class="text-xs text-text-secondary font-semibold leading-relaxed">${opt.interpretacion || ''}</p>
                </div>
                <div class="flex items-center gap-2 shrink-0">
                    <button type="button" id="btn-optimizar-horario-io" onclick="optimizarHorarioCursoIO()"
                            class="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-black shadow-md hover:scale-[1.02] active:scale-95 transition-all cursor-pointer">
                        <i data-lucide="sparkles" class="size-4 stroke-[2.5]"></i>
                        <span>Optimizar Horario</span>
                    </button>
                </div>
            </div>
        </div>

        <!-- 2. TARJETAS DE RESUMEN CLARAS PARA DOCENTES Y COORDINACIÓN -->
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-3.5">
            <div class="bg-white border border-[#c4eec0] rounded-xl p-4 shadow-2xs">
                <span class="text-[10px] font-black uppercase tracking-wider text-text-secondary block">Clases por Día</span>
                <div class="flex items-baseline gap-2 mt-1">
                    <strong class="text-2xl font-black text-sidebar">4 – 5</strong>
                    <span class="text-xs font-bold text-text-secondary">actual: ${est.clasesPorDiaMin ?? 0}–${est.clasesPorDiaMax ?? 0}</span>
                </div>
                <span class="text-[10px] font-semibold text-emerald-700">Mínimo 3, máximo 5 asignaturas diarias</span>
            </div>

            <div class="bg-white border border-[#c4eec0] rounded-xl p-4 shadow-2xs">
                <span class="text-[10px] font-black uppercase tracking-wider text-text-secondary block">Jornada y Descanso</span>
                <div class="flex items-baseline gap-2 mt-1">
                    <strong class="text-lg font-black text-sidebar">${est.horarioJornada || '7:00 a. m. – 12:00 p. m.'}</strong>
                </div>
                <span class="text-[10px] font-semibold ${est.cruzaDescanso ? 'text-alert-red font-bold' : 'text-emerald-700'}">Descanso: ${est.horarioDescanso || '9:30 a. m. – 10:00 a. m.'}</span>
            </div>

            <div class="bg-white border border-[#c4eec0] rounded-xl p-4 shadow-2xs">
                <span class="text-[10px] font-black uppercase tracking-wider text-text-secondary block">Plan del Grado ${est.grado || ''}</span>
                <div class="flex items-baseline gap-2 mt-1">
                    <strong class="text-2xl font-black text-sidebar">${totalPlanGrado} h</strong>
                    <span class="text-xs font-bold text-text-secondary">actual: ${rt.totalHorasReal ?? 0} h</span>
                </div>
                <span class="text-[10px] font-semibold text-emerald-700">${est.materiasActivasGrado || 9} asignaturas activas del grado</span>
            </div>

            <div class="bg-white border border-[#c4eec0] rounded-xl p-4 shadow-2xs">
                <span class="text-[10px] font-black uppercase tracking-wider text-text-secondary block">Horas en Aula y Práctica</span>
                <div class="flex items-baseline gap-2 mt-1">
                    <strong class="text-xl font-black text-emerald-700">${rt.x1TeoricasReal ?? 0}h / ${rt.x2PracticasReal ?? 0}h</strong>
                </div>
                <span class="text-[10px] font-semibold text-emerald-700">Mínimo ${params.mMinimoTeorico}h aula y ${params.pMinimoPractico}h práctica</span>
            </div>
        </div>

        <!-- 3. ¿POR QUÉ SE RECOMIENDA ESTE HORARIO? + COMPARATIVA ACTUAL VS RECOMENDADO -->
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-4">
            <!-- Explicación sencilla de las razones -->
            <div class="lg:col-span-6 bg-white border border-[#c4eec0] rounded-2xl p-4 shadow-2xs flex flex-col justify-between">
                <div>
                    <h5 class="text-xs font-black uppercase tracking-wider text-sidebar flex items-center gap-1.5 mb-3">
                        <i data-lucide="lightbulb" class="size-4 stroke-2"></i>
                        <span>¿Por qué se recomienda esta distribución?</span>
                    </h5>
                    <div class="space-y-2.5 text-xs">
                        <div class="p-3 rounded-xl bg-[#f7fcf6] border border-[#c4eec0]/70 flex items-start gap-2.5">
                            <i data-lucide="clock-3" class="size-4 text-sidebar shrink-0 mt-0.5"></i>
                            <div>
                                <strong class="font-black text-text-primary block">Horario completo hasta la salida con descanso respetado</strong>
                                <span class="text-text-secondary font-semibold">La jornada cubre de ${est.horarioJornada || '7:00 a. m. a 12:00 p. m.'}, respetando el descanso de ${est.horarioDescanso || '9:30 a 10:00 a. m.'} sin programar clases encima del receso.</span>
                            </div>
                        </div>
                        <div class="p-3 rounded-xl bg-[#f7fcf6] border border-[#c4eec0]/70 flex items-start gap-2.5">
                            <i data-lucide="layout-grid" class="size-4 text-emerald-700 shrink-0 mt-0.5"></i>
                            <div>
                                <strong class="font-black text-text-primary block">Entre 3 y 5 clases por día para cubrir todas las asignaturas</strong>
                                <span class="text-text-secondary font-semibold">En lugar de solo 2 bloques largos, se organizan 4 a 5 clases diarias de lunes a viernes para incluir las ${est.materiasActivasGrado || 9} materias del grado ${est.grado || ''} según su intensidad semanal.</span>
                            </div>
                        </div>
                        <div class="p-3 rounded-xl bg-[#f7fcf6] border border-[#c4eec0]/70 flex items-start gap-2.5">
                            <i data-lucide="flask-conical" class="size-4 text-emerald-700 shrink-0 mt-0.5"></i>
                            <div>
                                <strong class="font-black text-text-primary block">Uso ordenado de laboratorios y salas de sistemas</strong>
                                <span class="text-text-secondary font-semibold">Las clases de ciencias, sistemas y actividades ambientales se ubican en laboratorio o sala de cómputo asegurando al menos ${params.pMinimoPractico} horas prácticas sin saturar los espacios.</span>
                            </div>
                        </div>
                        <div class="p-3 rounded-xl bg-[#f7fcf6] border border-[#c4eec0]/70 flex items-start gap-2.5">
                            <i data-lucide="calendar-check-2" class="size-4 text-sidebar shrink-0 mt-0.5"></i>
                            <div>
                                <strong class="font-black text-text-primary block">Cero cruces entre docentes y salones</strong>
                                <span class="text-text-secondary font-semibold">Cada bloque verifica automáticamente que ningún docente ni aula tenga dos cursos asignados a la misma hora.</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Plan del Grado + Requisitos del Colegio -->
            <div class="lg:col-span-6 flex flex-col gap-4">
                <div class="bg-white border border-[#c4eec0] rounded-2xl p-4 shadow-2xs">
                    <div class="flex items-center justify-between gap-2 mb-3">
                        <h5 class="text-xs font-black uppercase tracking-wider text-sidebar flex items-center gap-1.5">
                            <i data-lucide="book-open" class="size-4 stroke-2"></i>
                            <span>Intensidad Horaria del Grado ${est.grado || ''}</span>
                        </h5>
                        <span class="text-[10px] font-bold text-text-secondary">Configurable en Configuración → Asignaturas</span>
                    </div>
                    <div class="max-h-44 overflow-y-auto pr-1">
                        <table class="w-full text-xs text-left">
                            <thead>
                                <tr class="border-b border-[#c4eec0] text-[10px] font-black uppercase text-text-secondary">
                                    <th class="pb-1.5">Asignatura</th>
                                    <th class="pb-1.5 text-center">Espacio</th>
                                    <th class="pb-1.5 text-right">Horas / Sem</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-[#c4eec0]/40 font-semibold">
                                ${planGrado.map(item => `
                                    <tr>
                                        <td class="py-1.5 font-bold text-text-primary">${item.materia}</td>
                                        <td class="py-1.5 text-center text-[11px] text-text-secondary">${item.tipo}</td>
                                        <td class="py-1.5 text-right font-black text-sidebar">${item.horasGrado} h</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>

                <!-- Verificación de Requisitos Institucionales -->
                <div class="bg-white border border-[#c4eec0] rounded-2xl p-4 shadow-2xs flex-1">
                    <h5 class="text-xs font-black uppercase tracking-wider text-sidebar flex items-center gap-1.5 mb-2.5">
                        <i data-lucide="shield-check" class="size-4 stroke-2"></i>
                        <span>Cumplimiento de Reglas Académicas del Colegio</span>
                    </h5>
                    <div class="space-y-1.5 text-xs">
                        ${Object.entries(opt.restriccionesOptimo || {}).map(([label, cumpleOpt]) => {
                            return `
                                <div class="flex items-center justify-between py-1.5 px-3 rounded-lg bg-[#f7fcf6] border border-[#c4eec0]/60">
                                    <span class="font-bold text-text-primary">${label}</span>
                                    <span class="font-black text-emerald-700">${cumpleOpt ? '✓ Cumple' : '✗ No cumple'}</span>
                                </div>
                            `;
                        }).join('')}
                    </div>
                </div>
            </div>
        </div>

        <!-- 4. OPCIONES DE DISTRIBUCIÓN + SIMULADOR SENCILLO -->
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-4">
            <!-- Opciones de distribución -->
            <div class="lg:col-span-6 bg-white border border-[#c4eec0] rounded-2xl p-4 shadow-2xs">
                <h5 class="text-xs font-black uppercase tracking-wider text-sidebar flex items-center gap-1.5 mb-3">
                    <i data-lucide="award" class="size-4 stroke-2"></i>
                    <span>Mejores Opciones de Distribución Semanal</span>
                </h5>
                <table class="w-full text-xs text-left">
                    <thead>
                        <tr class="border-b border-[#c4eec0] text-[10px] font-black uppercase text-text-secondary">
                            <th class="pb-2">Opción</th>
                            <th class="pb-2 text-center">Horas en Aula</th>
                            <th class="pb-2 text-center">Horas Prácticas</th>
                            <th class="pb-2 text-center">Total</th>
                            <th class="pb-2 text-center">Horas Libres</th>
                            <th class="pb-2 text-right">Uso de Espacios</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-[#c4eec0]/40 font-semibold">
                        ${ranking.map((r, i) => `
                            <tr class="${i === 0 ? 'bg-pill-green/40 font-black text-sidebar' : ''}">
                                <td class="py-2">${i === 0 ? '🥇 Recomendada' : 'Opción ' + r.posicion}</td>
                                <td class="py-2 text-center">${r.x1Teoricas} h</td>
                                <td class="py-2 text-center">${r.x2Practicas} h</td>
                                <td class="py-2 text-center">${r.totalHoras} h</td>
                                <td class="py-2 text-center">${r.holguraJornada} h</td>
                                <td class="py-2 text-right font-black">${i === 0 ? 'Muy equilibrado' : (i <= 2 ? 'Equilibrado' : 'Mayor ocupación')}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>

            <!-- Simulador sencillo para Coordinación / Docentes -->
            <div class="lg:col-span-6 bg-white border border-[#c4eec0] rounded-2xl p-4 shadow-2xs flex flex-col justify-between">
                <div>
                    <h5 class="text-xs font-black uppercase tracking-wider text-sidebar flex items-center gap-1.5 mb-1">
                        <i data-lucide="calculator" class="size-4 stroke-2"></i>
                        <span>Consultar Otra Distribución de Horas</span>
                    </h5>
                    <p class="text-[11px] text-text-secondary font-semibold mb-3">Ingrese una cantidad de horas en aula y horas prácticas para comprobar si cumple los requisitos del colegio.</p>

                    <div class="flex flex-wrap items-end gap-3 mb-3">
                        <div>
                            <label for="eval-io-x1" class="block text-[10px] font-black uppercase text-text-secondary mb-1">Horas en Aula</label>
                            <input id="eval-io-x1" type="number" min="0" max="40" value="16"
                                   class="h-9 w-28 px-3 rounded-xl border border-[#c4eec0] text-xs font-black text-sidebar focus:outline-none focus:ring-2 focus:ring-sidebar/20">
                        </div>
                        <div>
                            <label for="eval-io-x2" class="block text-[10px] font-black uppercase text-text-secondary mb-1">Horas Prácticas</label>
                            <input id="eval-io-x2" type="number" min="0" max="40" value="7"
                                   class="h-9 w-28 px-3 rounded-xl border border-[#c4eec0] text-xs font-black text-sidebar focus:outline-none focus:ring-2 focus:ring-sidebar/20">
                        </div>
                        <button type="button" id="btn-evaluar-io"
                                class="h-9 px-4 rounded-xl bg-sidebar hover:brightness-110 text-white text-xs font-black inline-flex items-center gap-1.5 cursor-pointer shadow-2xs">
                            <i data-lucide="check-circle-2" class="size-3.5 stroke-[2.5]"></i>
                            <span>Comprobar Horario</span>
                        </button>
                    </div>
                </div>
                <div id="eval-io-resultado" class="text-xs"></div>
            </div>
        </div>
    `;
}

function inicializarEvaluadorModeloIO() {
    const btn = document.getElementById('btn-evaluar-io');
    if (!btn) return;
    const ejecutar = async () => {
        const x1 = parseInt(document.getElementById('eval-io-x1')?.value || '14', 10);
        const x2 = parseInt(document.getElementById('eval-io-x2')?.value || '6', 10);
        const out = document.getElementById('eval-io-resultado');
        if (!out) return;
        try {
            const resp = await fetch(`/clases/horarios/modelo-io/evaluar?x1=${encodeURIComponent(x1)}&x2=${encodeURIComponent(x2)}`);
            const d = await resp.json();
            const ok = d.factible;
            const esLaRecomendada = ok && d.diferenciaContraOptimo === 0;
            const estadoOcupacion = esLaRecomendada
                ? 'Distribución ideal para aulas y laboratorios'
                : (ok ? 'Requiere mayor uso de salones o laboratorios' : 'No cumple las horas requeridas');
            out.innerHTML = `
                <div class="p-3 rounded-xl border ${ok ? 'bg-[#f2fbf0] border-[#8ce383]' : 'bg-red-50 border-red-200'}">
                    <div class="flex items-center justify-between gap-2 mb-2 flex-wrap">
                        <strong class="font-black ${ok ? 'text-sidebar' : 'text-alert-red'}">
                            ${ok ? '✓ Horario válido para el curso' : '✗ Horario no permitido'} — ${d.x1} h en aula y ${d.x2} h prácticas (${d.totalHoras} h en total)
                        </strong>
                        <span class="px-2.5 py-0.5 rounded-full bg-white border text-[11px] font-black ${ok ? 'text-sidebar border-[#8ce383]' : 'text-alert-red border-red-200'}">
                            ${estadoOcupacion}
                        </span>
                    </div>
                    <div class="grid grid-cols-1 sm:grid-cols-2 gap-1 text-[11px]">
                        ${Object.entries(d.detalle || {}).map(([k, v]) => `
                            <div class="flex items-center justify-between gap-2 bg-white/80 px-2.5 py-1 rounded border border-[#c4eec0]/40">
                                <span class="text-text-secondary font-semibold truncate">${k}</span>
                                <span class="font-black ${String(v).startsWith('✓') ? 'text-emerald-700' : 'text-alert-red'}">${v}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            `;
        } catch (e) {
            out.innerHTML = `<span class="text-alert-red font-bold">Error al comprobar el horario: ${e.message}</span>`;
        }
    };
    btn.addEventListener('click', ejecutar);
    ejecutar();
}
