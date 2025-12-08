import { useState, useEffect } from 'react';
import { Container, Title, Stack, Loader, Center, Modal, Button, Text, Group, Radio, Slider, ScrollArea } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { Calendar, dateFnsLocalizer } from 'react-big-calendar';
import { format, parse, startOfWeek, getDay } from 'date-fns';
import { es } from 'date-fns/locale';
import { notifications } from '@mantine/notifications';
import { availabilityAPI, gamesAPI } from '../lib/api';
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
    const [detailsOpened, { open: openDetails, close: closeDetails }] = useDisclosure(false);
    const [createOpened, { open: openCreate, close: closeCreate }] = useDisclosure(false);

    const [newSlot, setNewSlot] = useState<{ start: Date; end: Date } | null>(null);
    const [preferenceType, setPreferenceType] = useState('default');
    const [games, setGames] = useState<any[]>([]);
    const [customPreferences, setCustomPreferences] = useState<Record<string, number>>({});

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

    const loadGames = async () => {
        try {
            const response = await gamesAPI.getAll();
            setGames(response.data);
            // Initialize custom preferences with default 5
            const initialPrefs: Record<string, number> = {};
            response.data.forEach((g: any) => initialPrefs[g.id] = 5);
            setCustomPreferences(initialPrefs);
        } catch (error) {
            console.error('Error loading games:', error);
        }
    };

    useEffect(() => {
        loadAvailability();
        loadGames();
    }, []);

    const handleSelectSlot = async ({ start, end }: { start: Date; end: Date }) => {
        if (start.getHours() === 0 && start.getMinutes() === 0 &&
            end.getHours() === 0 && end.getMinutes() === 0) {
            notifications.show({
                title: 'Selección inválida',
                message: 'Por favor, selecciona una franja horaria específica, no días completos.',
                color: 'orange',
            });
            return;
        }

        setNewSlot({ start, end });
        setPreferenceType('default');
        openCreate();
    };

    const handleConfirmCreate = async () => {
        if (!newSlot) return;

        try {
            const payload: any = {
                startTime: newSlot.start.toISOString(),
                endTime: newSlot.end.toISOString(),
            };

            if (preferenceType === 'custom') {
                payload.preferences = Object.entries(customPreferences).map(([gameId, weight]) => ({
                    gameId,
                    weight
                }));
            }

            await availabilityAPI.create(payload);
            notifications.show({
                title: 'Disponibilidad añadida',
                message: 'Tu franja horaria ha sido guardada',
                color: 'green',
            });
            closeCreate();
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
        openDetails();
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
            closeDetails();
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

            <Modal opened={detailsOpened} onClose={closeDetails} title="Detalles de Disponibilidad" centered withinPortal>
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
                            <Button variant="default" onClick={closeDetails}>Cerrar</Button>
                            <Button color="red" onClick={handleDeleteEvent}>Eliminar</Button>
                        </Group>
                    </Stack>
                )}
            </Modal>

            <Modal opened={createOpened} onClose={closeCreate} title="Nueva Disponibilidad" centered size="lg">
                <Stack>
                    <Text>¿Qué preferencias quieres usar para esta sesión?</Text>
                    <Radio.Group value={preferenceType} onChange={setPreferenceType}>
                        <Group mt="xs">
                            <Radio value="default" label="Mis preferencias habituales" />
                            <Radio value="custom" label="Personalizar para esta sesión" />
                        </Group>
                    </Radio.Group>

                    {preferenceType === 'custom' && (
                        <ScrollArea h={300} type="always" offsetScrollbars>
                            <Stack gap="md" pr="md">
                                {games.map(game => (
                                    <div key={game.id}>
                                        <Text size="sm" fw={500}>{game.title}</Text>
                                        <Slider
                                            value={customPreferences[game.id] ?? 5}
                                            onChange={(val) => setCustomPreferences(prev => ({ ...prev, [game.id]: val }))}
                                            min={0}
                                            max={10}
                                            marks={[
                                                { value: 0, label: '0' },
                                                { value: 5, label: '5' },
                                                { value: 10, label: '10' },
                                            ]}
                                        />
                                    </div>
                                ))}
                            </Stack>
                        </ScrollArea>
                    )}

                    <Group justify="flex-end" mt="md">
                        <Button variant="default" onClick={closeCreate}>Cancelar</Button>
                        <Button onClick={handleConfirmCreate}>Guardar</Button>
                    </Group>
                </Stack>
            </Modal>
        </>
    );
}
