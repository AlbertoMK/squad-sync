import { useState, useEffect } from 'react';
import { Container, Title, Stack, Loader, Center, Modal, Button, Text, Group } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { Calendar, dateFnsLocalizer } from 'react-big-calendar';
import { format, parse, startOfWeek, getDay } from 'date-fns';
import { es } from 'date-fns/locale';
import { notifications } from '@mantine/notifications';
import { availabilityAPI } from '../lib/api';
import './AvailabilityCalendar.css';

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
    const [date, setDate] = useState(new Date());
    const [selectedEvent, setSelectedEvent] = useState<AvailabilityEvent | null>(null);
    const [opened, { open, close }] = useDisclosure(false);

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
        // Prevent full day selection (often triggered from month view or day headers)
        // If start and end are 00:00:00, it's likely a full day selection
        if (start.getHours() === 0 && start.getMinutes() === 0 &&
            end.getHours() === 0 && end.getMinutes() === 0) {
            notifications.show({
                title: 'Selección inválida',
                message: 'Por favor, selecciona una franja horaria específica, no días completos.',
                color: 'orange',
            });
            return;
        }

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

    const handleSelectEvent = (event: AvailabilityEvent) => {
        setSelectedEvent(event);
        open();
    };

    const handleDeleteEvent = async () => {
        if (!selectedEvent) return;

        try {
            await availabilityAPI.delete(selectedEvent.id);
            notifications.show({
                title: 'Disponibilidad eliminada',
                message: 'La franja horaria ha sido eliminada',
                color: 'blue',
            });
            close();
            await loadAvailability();
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al eliminar disponibilidad',
                color: 'red',
            });
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
        <>
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
                            date={date}
                            onNavigate={setDate}
                            views={['week', 'work_week', 'day', 'agenda']}
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

            <Modal opened={opened} onClose={close} title="Detalles de Disponibilidad" centered withinPortal>
                {selectedEvent && (
                    <Stack>
                        <Text fw={500}>{selectedEvent.title}</Text>
                        <Group>
                            <Text size="sm" c="dimmed">Desde:</Text>
                            <Text size="sm">{format(selectedEvent.start, 'PPpp', { locale: es })}</Text>
                        </Group>
                        <Group>
                            <Text size="sm" c="dimmed">Hasta:</Text>
                            <Text size="sm">{format(selectedEvent.end, 'PPpp', { locale: es })}</Text>
                        </Group>

                        <Group justify="flex-end" mt="md">
                            <Button variant="default" onClick={close}>Cerrar</Button>
                            <Button color="red" onClick={handleDeleteEvent}>Eliminar</Button>
                        </Group>
                    </Stack>
                )}
            </Modal>
        </>
    );
}
