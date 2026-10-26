/**
 * dashboard-charts.js
 * Inicializa los gráficos Chart.js para el dashboard principal.
 * Requiere Chart.js cargado previamente.
 */

// Inicializar iconos de Lucide
lucide.createIcons();

// Configuración de Gráficos con Chart.js
document.addEventListener("DOMContentLoaded", function () {
    // --- 1. GRÁFICO HISTÓRICO DE ASISTENCIA ---
    const canvasAttendance = document.getElementById('attendanceChart');
    if (canvasAttendance) {
        const ctxAttendance = canvasAttendance.getContext('2d');
        
        let labels = [];
        let dataValues = [];

        if (canvasAttendance.dataset.labels && canvasAttendance.dataset.valores) {
            labels = canvasAttendance.dataset.labels.split(',').map(function (s) { return s.trim(); }).filter(function (s) { return s.length > 0; });
            dataValues = canvasAttendance.dataset.valores.split(',').map(function (s) { return parseFloat(s.trim()); }).filter(function (n) { return !isNaN(n); });
        }

        if (!labels.length || !dataValues.length || labels.length !== dataValues.length) {
            labels = Array.from({length: 15}, function (_, i) { return `Día ${i + 1}`; });
            dataValues = [93.1, 94.0, 92.5, 93.8, 94.2, 93.0, 92.8, 93.9, 94.5, 93.4, 94.0, 93.7, 94.1, 93.5, 94.2];
        }

        // Crear degradado verde para el fondo del área
        const gradient = ctxAttendance.createLinearGradient(0, 0, 0, 200);
        gradient.addColorStop(0, 'rgba(20, 95, 34, 0.25)');
        gradient.addColorStop(1, 'rgba(20, 95, 34, 0)');

        const minVal = Math.min.apply(null, dataValues);
        const yMin = Math.max(0, Math.min(85, Math.floor(minVal - 2)));

        new Chart(ctxAttendance, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Asistencia (%)',
                    data: dataValues,
                    borderColor: '#145f22',
                    borderWidth: 3,
                    backgroundColor: gradient,
                    fill: true,
                    tension: 0.4,
                    pointRadius: 0,
                    pointHoverRadius: 6,
                    pointHoverBackgroundColor: '#145f22',
                    pointHoverBorderColor: '#ffffff',
                    pointHoverBorderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: '#173518',
                        titleColor: '#ffffff',
                        bodyColor: '#ecffe4',
                        titleFont: { weight: 'bold' },
                        displayColors: false,
                        callbacks: {
                            label: function(context) {
                                return `Asistencia: ${context.parsed.y}%`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: {
                            display: false
                        },
                        ticks: {
                            color: '#6f826f',
                            font: {
                                size: 10,
                                weight: 'bold'
                            }
                        }
                    },
                    y: {
                        min: yMin,
                        max: 100,
                        grid: {
                            color: 'rgba(196, 238, 192, 0.25)',
                            lineWidth: 1
                        },
                        ticks: {
                            color: '#6f826f',
                            font: {
                                size: 10,
                                weight: 'bold'
                            },
                            callback: function(value) {
                                return value + '%';
                            }
                        }
                    }
                }
            }
        });
    }

    // --- 2. GRÁFICO DE DISTRIBUCIÓN DE ALUMNOS ---
    const canvasDistribution = document.getElementById('distributionChart');
    if (canvasDistribution) {
        const ctxDistribution = canvasDistribution.getContext('2d');
        const pPrimaria = parseInt(canvasDistribution.dataset.primaria, 10);
        const pSecundaria = parseInt(canvasDistribution.dataset.secundaria, 10);
        const pMedia = parseInt(canvasDistribution.dataset.media, 10);

        const dataPrimaria = !isNaN(pPrimaria) ? pPrimaria : 45;
        const dataSecundaria = !isNaN(pSecundaria) ? pSecundaria : 40;
        const dataMedia = !isNaN(pMedia) ? pMedia : 15;

        new Chart(ctxDistribution, {
            type: 'doughnut',
            data: {
                labels: ['Primaria', 'Secundaria', 'Media'],
                datasets: [{
                    data: [dataPrimaria, dataSecundaria, dataMedia],
                    backgroundColor: ['#145f22', '#34a853', '#aacf7f'],
                    borderColor: '#ffffff',
                    borderWidth: 2.5,
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '75%',
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: '#173518',
                        titleColor: '#ffffff',
                        bodyColor: '#ecffe4',
                        displayColors: true,
                        callbacks: {
                            label: function(context) {
                                return ` ${context.label}: ${context.parsed}%`;
                            }
                        }
                    }
                }
            }
        });
    }
});
