function sigaChatbotWidget() {
    const GREETING_HIDE_MS = 5000;
    const FAB_HIDE_MS = 12000;
    const PRIVACY_HIDE_MS = 5500;

    return {
        isOpen: false,
        isFabVisible: true,
        showGreetingBubble: true,
        showHideHint: false,
        showPrivacyNotice: false,
        showSuggestions: true,
        inputMessage: '',
        isLoading: false,
        messages: [],
        greetingTimer: null,
        fabHideTimer: null,
        hintTimer: null,
        privacyTimer: null,

        init() {
            // Cargar estado de conversación desde sessionStorage para no perder mensajes al navegar
            try {
                // Limpieza obligatoria de versiones previas con 'Catalina'
                sessionStorage.removeItem('siga_chat_history');
                sessionStorage.removeItem('siga_chat_greeting_dismissed');

                // Mitigación de seguridad multi-usuario en equipos compartidos:
                // Si el usuario autenticado actual difiere del guardado, purgar historial de notas
                const currentUserEmail = this.$el.getAttribute('data-current-user') || '';
                const savedUserEmail = sessionStorage.getItem('siga_mangle_chat_user');
                if (savedUserEmail && currentUserEmail && savedUserEmail !== currentUserEmail) {
                    sessionStorage.removeItem('siga_mangle_chat_history');
                    sessionStorage.removeItem('siga_mangle_fab_hidden');
                    sessionStorage.removeItem('siga_mangle_greeting_dismissed');
                    sessionStorage.removeItem('siga_mangle_hide_toast_shown');
                    sessionStorage.removeItem('siga_mangle_privacy_seen');
                }
                if (currentUserEmail) {
                    sessionStorage.setItem('siga_mangle_chat_user', currentUserEmail);
                }

                // Vincular purga inmediata de sessionStorage a cualquier evento submit de logout
                document.querySelectorAll('form[action*="logout"]').forEach(f => {
                    if (!f.dataset.chatCleanBound) {
                        f.dataset.chatCleanBound = 'true';
                        f.addEventListener('submit', () => {
                            sessionStorage.removeItem('siga_mangle_chat_history');
                            sessionStorage.removeItem('siga_mangle_chat_user');
                            sessionStorage.removeItem('siga_chat_open');
                            sessionStorage.removeItem('siga_mangle_fab_hidden');
                            sessionStorage.removeItem('siga_mangle_greeting_dismissed');
                            sessionStorage.removeItem('siga_mangle_hide_toast_shown');
                            sessionStorage.removeItem('siga_mangle_privacy_seen');
                        });
                    }
                });

                const savedMessages = sessionStorage.getItem('siga_mangle_chat_history');
                if (savedMessages && !savedMessages.includes('Catalina')) {
                    this.messages = JSON.parse(savedMessages);
                } else {
                    sessionStorage.removeItem('siga_mangle_chat_history');
                    this.inicializarBienvenida();
                }

                const wasOpen = sessionStorage.getItem('siga_chat_open');
                const wasFabHidden = sessionStorage.getItem('siga_mangle_fab_hidden') === 'true';
                const greetingDismissed = sessionStorage.getItem('siga_mangle_greeting_dismissed') === 'true';

                if (wasOpen === 'true') {
                    this.isOpen = true;
                    this.isFabVisible = true;
                    this.showGreetingBubble = false;
                    this.triggerPrivacyNotice();
                    this.$nextTick(() => this.scrollToBottom());
                } else if (wasFabHidden) {
                    this.isFabVisible = false;
                    this.showGreetingBubble = false;
                } else {
                    this.showGreetingBubble = !greetingDismissed;
                    this.scheduleGreetingHide();
                    this.scheduleAutoHide();
                }
            } catch (e) {
                this.inicializarBienvenida();
            }
        },

        triggerPrivacyNotice() {
            try {
                if (sessionStorage.getItem('siga_mangle_privacy_seen') === 'true') {
                    return;
                }
                sessionStorage.setItem('siga_mangle_privacy_seen', 'true');
            } catch (e) {}
            this.showPrivacyNotice = true;
            if (this.privacyTimer) clearTimeout(this.privacyTimer);
            this.privacyTimer = setTimeout(() => {
                this.showPrivacyNotice = false;
            }, PRIVACY_HIDE_MS);
        },

        dismissPrivacyNotice() {
            this.showPrivacyNotice = false;
            if (this.privacyTimer) clearTimeout(this.privacyTimer);
            try { sessionStorage.setItem('siga_mangle_privacy_seen', 'true'); } catch (e) {}
        },

        scheduleGreetingHide() {
            if (this.greetingTimer) clearTimeout(this.greetingTimer);
            if (!this.showGreetingBubble || this.isOpen) return;
            this.greetingTimer = setTimeout(() => {
                this.showGreetingBubble = false;
                try { sessionStorage.setItem('siga_mangle_greeting_dismissed', 'true'); } catch (e) {}
            }, GREETING_HIDE_MS);
        },

        scheduleAutoHide() {
            if (this.fabHideTimer) clearTimeout(this.fabHideTimer);
            if (this.isOpen || !this.isFabVisible) return;
            this.fabHideTimer = setTimeout(() => {
                if (!this.isOpen) {
                    this.dismissFab(true);
                }
            }, FAB_HIDE_MS);
        },

        pauseAutoHide() {
            if (this.greetingTimer) clearTimeout(this.greetingTimer);
            if (this.fabHideTimer) clearTimeout(this.fabHideTimer);
        },

        dismissFab(esAutomatico = false) {
            this.pauseAutoHide();
            this.showGreetingBubble = false;
            this.isFabVisible = false;
            try {
                sessionStorage.setItem('siga_mangle_fab_hidden', 'true');
                sessionStorage.setItem('siga_mangle_greeting_dismissed', 'true');
                if (!esAutomatico || !sessionStorage.getItem('siga_mangle_hide_toast_shown')) {
                    sessionStorage.setItem('siga_mangle_hide_toast_shown', 'true');
                    this.showHideHint = true;
                    if (this.hintTimer) clearTimeout(this.hintTimer);
                    this.hintTimer = setTimeout(() => { this.showHideHint = false; }, 2800);
                }
            } catch (e) {}
        },

        toggleFromShortcut() {
            this.showHideHint = false;
            this.isFabVisible = true;
            try { sessionStorage.removeItem('siga_mangle_fab_hidden'); } catch (e) {}
            this.toggleChat();
        },

        handleGlobalShortcut(event) {
            if (event.altKey && !event.ctrlKey && !event.metaKey && event.key && event.key.toLowerCase() === 'm') {
                event.preventDefault();
                this.toggleFromShortcut();
                return;
            }
            if (event.key === 'Escape' && this.isOpen) {
                this.isOpen = false;
                try { sessionStorage.setItem('siga_chat_open', 'false'); } catch (e) {}
            }
        },

        inicializarBienvenida() {
            const container = document.getElementById('siga-chatbot-container') || this.$el;
            const userName = (container ? (container.getAttribute('data-user-name') || '') : '').trim();
            const userRole = (container ? (container.getAttribute('data-user-role') || '') : '').trim().toUpperCase();
            let saludo = '¡Hola! 🌿';
            if (userName) {
                saludo = userRole === 'DOCENTE'
                    ? `¡Hola, Prof. ${userName}! 🌿`
                    : `¡Hola, ${userName}! 🌿`;
            }
            let enfoque = 'Puedo ayudarte con derechos, deberes, uniforme, excusas médicas, estímulos, Gobierno Escolar, evaluación (SIEACI) o PRAE.';
            if (userRole === 'ESTUDIANTE') {
                enfoque = 'Puedes preguntarme directamente por tus notas, tu horario de hoy, tus asistencias o cualquier duda del Manual de Convivencia, SIEACI y PRAE.';
            } else if (userRole === 'DOCENTE') {
                enfoque = 'Puedes consultarme tu horario de clases de hoy, tus grupos asignados, criterios del SIEACI o procedimientos de convivencia.';
            } else if (userRole === 'ADMIN' || userRole === 'PERSONAL_ADMINISTRATIVO') {
                enfoque = 'Puedes consultarme estadísticas institucionales, búsqueda rápida de estudiantes o lineamientos del Manual de Convivencia y Gobierno Escolar.';
            }
            this.messages = [
                {
                    sender: 'bot',
                    text: `${saludo} Soy **Mangle**, tu asistente virtual de la IEACI.\n\n${enfoque}`,
                    referencias: [],
                    herramientas: [],
                    latencia: null,
                    proveedor: 'Mangle · IEACI'
                }
            ];
            this.guardarEnStorage();
        },

        toggleChat() {
            this.isOpen = !this.isOpen;
            if (this.isOpen) {
                this.pauseAutoHide();
                this.isFabVisible = true;
                this.showGreetingBubble = false;
                this.showHideHint = false;
                this.triggerPrivacyNotice();
                sessionStorage.setItem('siga_chat_open', 'true');
                this.$nextTick(() => {
                    this.scrollToBottom();
                    if (this.$refs.inputField) this.$refs.inputField.focus();
                });
            } else {
                sessionStorage.setItem('siga_chat_open', 'false');
                this.scheduleAutoHide();
            }
        },

        dismissGreeting() {
            this.showGreetingBubble = false;
            try { sessionStorage.setItem('siga_mangle_greeting_dismissed', 'true'); } catch (e) {}
        },

        reiniciarConversacion() {
            sessionStorage.removeItem('siga_chat_history');
            sessionStorage.removeItem('siga_mangle_chat_history');
            this.inicializarBienvenida();
            this.scrollToBottom();
        },

        enviarPreguntaRapida(pregunta) {
            this.inputMessage = pregunta;
            this.enviarMensaje();
        },

        async enviarMensaje() {
            const texto = (this.inputMessage || '').trim();
            if (!texto || this.isLoading) return;

            this.messages.push({
                sender: 'user',
                text: texto
            });
            this.inputMessage = '';
            this.isLoading = true;
            this.guardarEnStorage();
            this.$nextTick(() => this.scrollToBottom());

            const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-XSRF-TOKEN';

            try {
                const response = await fetch('/chat/api/mensaje', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        [csrfHeader]: csrfToken
                    },
                    body: JSON.stringify({
                        mensaje: texto,
                        rolUsuario: null
                    })
                });

                if (!response.ok) {
                    throw new Error('Error en el servidor (' + response.status + ')');
                }

                const data = await response.json();
                this.messages.push({
                    sender: 'bot',
                    text: data.respuesta || 'Respuesta generada.',
                    referencias: data.referenciasNormativas || [],
                    herramientas: data.herramientasInvocadas || [],
                    latencia: data.latenciaMs,
                    proveedor: data.proveedor || (data.procesadoLocalmente ? 'Groq / LLM' : 'Motor Normativo')
                });
            } catch (err) {
                this.messages.push({
                    sender: 'bot',
                    text: '⚠️ Disculpa, ocurrió una dificultad técnica temporal al contactar al asistente. Por favor intenta de nuevo en unos instantes.',
                    referencias: [],
                    herramientas: [],
                    latencia: null,
                    proveedor: 'Servicio Institucional'
                });
            } finally {
                this.isLoading = false;
                this.guardarEnStorage();
                this.$nextTick(() => this.scrollToBottom());
            }
        },

        guardarEnStorage() {
            try {
                sessionStorage.setItem('siga_mangle_chat_history', JSON.stringify(this.messages));
            } catch (e) {}
        },

        scrollToBottom() {
            const el = document.getElementById('floating-chat-scroll');
            if (el) {
                el.scrollTop = el.scrollHeight;
            }
        },

        formatearMensaje(text) {
            if (!text) return '';
            let formatted = text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
            formatted = formatted.replace(/^• /gm, '• ');
            return formatted;
        }
    };
}
