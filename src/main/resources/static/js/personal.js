function openModalPersonal() {
    const modal = document.getElementById('modal-personal');
    const dialog = document.getElementById('modal-personal-dialog');
    if (!modal || !dialog) return;

    cerrarAsignaturasDropdown();
    actualizarCamposTipoPersonal();
    actualizarResumenAsignaturas();
    ocultarErrorAsignaturas();

    modal.classList.remove('hidden');
    setTimeout(() => {
        dialog.classList.remove('scale-95', 'opacity-0');
        dialog.classList.add('scale-100', 'opacity-100');
    }, 10);
}

function closeModalPersonal() {
    const modal = document.getElementById('modal-personal');
    const dialog = document.getElementById('modal-personal-dialog');
    if (!modal || !dialog) return;

    cerrarAsignaturasDropdown();
    dialog.classList.remove('scale-100', 'opacity-100');
    dialog.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        modal.classList.add('hidden');
    }, 300);
}

function toggleAsignaturasDropdown(event) {
    if (event) event.stopPropagation();
    const panel = document.getElementById('dropdown-asignaturas-panel');
    const trigger = document.getElementById('asignaturas-trigger');
    const chevron = document.getElementById('asignaturas-chevron');
    if (!panel) return;

    const willOpen = panel.classList.contains('hidden');
    panel.classList.toggle('hidden', !willOpen);
    if (trigger) trigger.setAttribute('aria-expanded', String(willOpen));
    if (chevron) chevron.classList.toggle('rotate-180', willOpen);

    if (willOpen) {
        const searchInput = document.getElementById('filtro-asignaturas');
        if (searchInput) {
            setTimeout(() => searchInput.focus(), 50);
        }
        panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
}

function cerrarAsignaturasDropdown() {
    const panel = document.getElementById('dropdown-asignaturas-panel');
    const trigger = document.getElementById('asignaturas-trigger');
    const chevron = document.getElementById('asignaturas-chevron');
    if (panel) panel.classList.add('hidden');
    if (trigger) trigger.setAttribute('aria-expanded', 'false');
    if (chevron) chevron.classList.remove('rotate-180');
}

function actualizarCamposTipoPersonal() {
    const tipoSelect = document.getElementById('p-tipo');
    const grupoAsignaturas = document.getElementById('grupo-asignaturas');
    const grupoCargo = document.getElementById('grupo-cargo');
    const inputCargo = document.getElementById('p-cargo');
    if (!tipoSelect || !grupoAsignaturas || !grupoCargo) return;

    const esDocente = tipoSelect.value === 'DOCENTE';
    if (esDocente) {
        grupoAsignaturas.classList.remove('hidden');
        grupoCargo.classList.add('hidden');
        if (inputCargo) inputCargo.required = false;
    } else {
        grupoAsignaturas.classList.add('hidden');
        grupoCargo.classList.remove('hidden');
        if (inputCargo) inputCargo.required = true;
        cerrarAsignaturasDropdown();
        ocultarErrorAsignaturas();
    }
}

function actualizarEstadoAsignatura(checkbox) {
    const label = checkbox.closest('.asignatura-option');
    if (label) {
        if (checkbox.checked) {
            label.classList.add('border-sidebar', 'bg-pill-green/70', 'ring-1', 'ring-sidebar/20');
            label.classList.remove('border-border', 'bg-white');
        } else {
            label.classList.remove('border-sidebar', 'bg-pill-green/70', 'ring-1', 'ring-sidebar/20');
            label.classList.add('border-border', 'bg-white');
        }
    }
    actualizarResumenAsignaturas();
    ocultarErrorAsignaturas();
}

function actualizarResumenAsignaturas() {
    const badge = document.getElementById('asignaturas-count');
    const content = document.getElementById('asignaturas-trigger-content');
    const checked = Array.from(document.querySelectorAll('#lista-asignaturas-modal .asignatura-checkbox:checked'));

    if (badge) {
        badge.textContent = checked.length === 1 ? '1 seleccionada' : checked.length + ' seleccionadas';
    }

    if (content) {
        content.innerHTML = '';
        if (checked.length === 0) {
            const placeholder = document.createElement('span');
            placeholder.className = 'text-text-muted font-semibold';
            placeholder.textContent = 'Seleccionar asignaturas...';
            content.appendChild(placeholder);
        } else {
            checked.forEach(cb => {
                const pill = document.createElement('span');
                pill.className = 'inline-flex items-center px-2 py-0.5 rounded-md bg-pill-green text-sidebar font-bold text-[11px] border border-[#cbe5c7]';
                pill.textContent = cb.dataset.label || cb.closest('.asignatura-option')?.dataset.nombre || 'Asignatura';
                content.appendChild(pill);
            });
        }
    }
}

function filtrarAsignaturasModal(query) {
    const term = (query || '').trim().toLowerCase();
    document.querySelectorAll('#lista-asignaturas-modal .asignatura-option').forEach(opt => {
        const nombre = (opt.dataset.nombre || opt.textContent || '').toLowerCase();
        opt.classList.toggle('hidden', term.length > 0 && !nombre.includes(term));
    });
}

function ocultarErrorAsignaturas() {
    const err = document.getElementById('asignaturas-error');
    if (err) err.classList.add('hidden');
}

document.addEventListener('click', (e) => {
    const grupo = document.getElementById('grupo-asignaturas');
    const panel = document.getElementById('dropdown-asignaturas-panel');
    if (grupo && panel && !panel.classList.contains('hidden') && !grupo.contains(e.target)) {
        cerrarAsignaturasDropdown();
    }
});

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('form-personal');
    if (form) {
        form.addEventListener('submit', (e) => {
            const tipoSelect = document.getElementById('p-tipo');
            const lista = document.getElementById('lista-asignaturas-modal');
            if (tipoSelect && tipoSelect.value === 'DOCENTE' && lista) {
                const hayOpciones = lista.querySelectorAll('.asignatura-checkbox').length > 0;
                const seleccionadas = lista.querySelectorAll('.asignatura-checkbox:checked').length;
                if (hayOpciones && seleccionadas === 0) {
                    e.preventDefault();
                    const err = document.getElementById('asignaturas-error');
                    if (err) err.classList.remove('hidden');
                    const panel = document.getElementById('dropdown-asignaturas-panel');
                    if (panel && panel.classList.contains('hidden')) {
                        toggleAsignaturasDropdown();
                    }
                }
            }
        });
    }
    actualizarCamposTipoPersonal();
    actualizarResumenAsignaturas();
});

function filtrarPersonal(tipo) {
    const rows = document.querySelectorAll('.personal-row');
    rows.forEach(r => {
        if (tipo === 'todos' || r.dataset.tipo === tipo) {
            r.classList.remove('hidden');
        } else {
            r.classList.add('hidden');
        }
    });
}