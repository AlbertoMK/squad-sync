import { useState, useEffect } from 'react';
import { Container, Title, Stack, Loader, Center } from '@mantine/core';
import { Calendar, dateFnsLocalizer } from 'react-big-calendar';
import { format, parse, startOfWeek, getDay } from 'date-fns';
import { es } from 'date-fns/locale';
import { notifications } from '@mantine/notifications';
import { availabilityAPI } from '../lib/api';

const locales = {
    es: es,
};

const localizer = dateFnsLocalizer({
    format,
    parse,
    startOfWeek,
    getDay,
    locales,
});

interface AvailabilityEvent {
    id: string;
    title: string;
    start: Date;
    end: Date;
    resource?: any;
}

type CalendarView = 'month' | 'week' | 'work_week' | 'day' | 'agenda';

export default function AvailabilityCalendar() {
    const [events, setEvents] = useState<AvailabilityEvent[]>([]);
    const [loading, setLoading] = useState(true);
    const [view, setView] = useState<CalendarView>('week');

    const loadAvailability = async () => {
        try {
            const response = await availabilityAPI.getAll();
            const formattedEvents = response.data.map((slot: any) => ({
                id: slot.id,
                title: slot.game ? `Disponible para ${slot.game.title}` : 'Disponible',
                start: new Date(slot.startTime),
                end: new Date(slot.endTime),
                resource: slot,
            }));
            setEvents(formattedEvents);
        } catch (error) {
            console.error('Error loading availability:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadAvailability();
    }, []);

    const handleSelectSlot = async ({ start, end }: { start: Date; end: Date }) => {
        try {
            await availabilityAPI.create({
                startTime: start.toISOString(),
                endTime: end.toISOString(),
            });
            notifications.show({
                title: 'Disponibilidad añadida',
                message: 'Tu franja horaria ha sido guardada',
                color: 'green',
            });
            await loadAvailability();
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al crear disponibilidad',
                color: 'red',
            });
        }
    };

    const handleSelectEvent = async (event: AvailabilityEvent) => {
        const confirmed = window.confirm('¿Quieres eliminar esta disponibilidad?');
        if (confirmed) {
            try {
                await availabilityAPI.delete(event.id);
                notifications.show({
                    title: 'Disponibilidad eliminada',
                    message: 'La franja horaria ha sido eliminada',
                    color: 'blue',
                });
                await loadAvailability();
            } catch (error: any) {
                notifications.show({
                    title: 'Error',
                    message: error.response?.data?.error || 'Error al eliminar disponibilidad',
                    color: 'red',
                });
            }
        }
    };

    if (loading) {
        return (
            <Center h={400}>
                <Loader size="xl" />
            </Center>
        );
    }

    return (
        <Container size="xl">
            <Stack gap="lg">
                <Title order={2}>Mi Disponibilidad</Title>

                <div style={{ height: '600px' }}>
                    <Calendar
                        localizer={localizer}
                        events={events}
                        startAccessor="start"
                        endAccessor="end"
                        style={{ height: '100%' }}
                        selectable
                        onSelectSlot={handleSelectSlot}
                        onSelectEvent={handleSelectEvent}
                        view={view}
                        onView={setView}
                        defaultView="week"
                        culture="es"
                        messages={{
                            next: 'Siguiente',
                            previous: 'Anterior',
                            today: 'Hoy',
                            month: 'Mes',
                            week: 'Semana',
                            day: 'Día',
                            agenda: 'Agenda',
                            date: 'Fecha',
                            time: 'Hora',
                            event: 'Evento',
                            noEventsInRange: 'No hay disponibilidad en este rango',
                        }}
                    />
                </div>
            </Stack>
        </Container>
    );
}
