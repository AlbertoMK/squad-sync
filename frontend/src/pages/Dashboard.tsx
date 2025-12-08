import { useState, useEffect } from 'react';
import {
    Container,
    Title,
    Grid,
    Card,
    Text,
    Stack,
    Button,
    Group,
    Badge,
    Loader,
    Center,
    Modal,
    Radio,
} from '@mantine/core';
import { IconRefresh, IconCalendar, IconUsers, IconCheck, IconX } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { matchmakingAPI, sessionsAPI } from '../lib/api';
import { format } from 'date-fns';
import { es } from 'date-fns/locale';
import { useDisclosure } from '@mantine/hooks';
import { useAuth } from '../contexts/AuthContext';

interface Session {
    id: string;
    startTime: string;
    endTime: string;
    sessionScore: number;
    game: {
        id: string;
        title: string;
        genre?: string;
        coverImageUrl?: string;
    };
    playerIds: string[]; // Changed to array based on backend DTO update
    players: {
        userId: string;
        username: string;
        avatarColor?: string;
        status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
    }[];
    status: 'PRELIMINARY' | 'CONFIRMED' | 'CANCELLED';
}

export default function Dashboard() {
    const { user } = useAuth();
    const [sessions, setSessions] = useState<Session[]>([]);
    const [loading, setLoading] = useState(true);
    const [runningMatchmaking, setRunningMatchmaking] = useState(false);

    // Rejection Modal State
    const [rejectModalOpened, { open: openRejectModal, close: closeRejectModal }] = useDisclosure(false);
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [rejectionReason, setRejectionReason] = useState('NOT_AVAILABLE');

    const loadSessions = async () => {
        try {
            const response = await matchmakingAPI.getSessions();
            setSessions(response.data);
        } catch (error) {
            console.error('Error loading sessions:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadSessions();
    }, []);

    const handleRunMatchmaking = async () => {
        setRunningMatchmaking(true);
        try {
            const response = await matchmakingAPI.run();
            notifications.show({
                title: '¡Matchmaking completado!',
                message: `Se crearon ${response.data.length} sesiones`, // Response is list of sessions
                color: 'green',
            });
            await loadSessions();
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al ejecutar matchmaking',
                color: 'red',
            });
        } finally {
            setRunningMatchmaking(false);
        }
    };

    const handleAcceptSession = async (sessionId: string) => {
        try {
            await sessionsAPI.accept(sessionId);
            notifications.show({
                title: 'Sesión aceptada',
                message: 'Has confirmado tu asistencia',
                color: 'green',
            });
            await loadSessions(); // Reload to update status if we were tracking it locally
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al aceptar sesión',
                color: 'red',
            });
        }
    };

    const handleOpenRejectModal = (sessionId: string) => {
        setSelectedSessionId(sessionId);
        setRejectionReason('NOT_AVAILABLE');
        openRejectModal();
    };

    const handleConfirmReject = async () => {
        if (!selectedSessionId) return;

        try {
            await sessionsAPI.reject(selectedSessionId, rejectionReason);
            notifications.show({
                title: 'Sesión rechazada',
                message: rejectionReason === 'NOT_AVAILABLE'
                    ? 'Se ha eliminado tu disponibilidad para esta franja.'
                    : 'Has rechazado esta propuesta de juego.',
                color: 'blue',
            });
            closeRejectModal();
            await loadSessions();
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al rechazar sesión',
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
        <Container size="xl">
            <Stack gap="lg">
                <Group justify="space-between">
                    <Title order={2}>Dashboard</Title>
                    {user?.role === 'ADMIN' && (
                        <Button
                            leftSection={<IconRefresh size={16} />}
                            onClick={handleRunMatchmaking}
                            loading={runningMatchmaking}
                        >
                            Ejecutar Matchmaking
                        </Button>
                    )}
                </Group>

                <Grid>
                    <Grid.Col span={{ base: 12, md: 4 }}>
                        <Card shadow="sm" padding="lg" radius="md" withBorder>
                            <Stack gap="xs">
                                <Group>
                                    <IconCalendar size={24} />
                                    <Text size="sm" c="dimmed">
                                        Próximas Sesiones
                                    </Text>
                                </Group>
                                <Text size="xl" fw={700}>
                                    {sessions.length}
                                </Text>
                            </Stack>
                        </Card>
                    </Grid.Col>
                </Grid>

                {sessions.length === 0 ? (
                    <Card shadow="sm" padding="lg" radius="md" withBorder>
                        <Text c="dimmed" ta="center">
                            No hay sesiones propuestas aún.
                            {user?.role === 'ADMIN' && ' Ejecuta el matchmaking para encontrar partidas.'}
                        </Text>
                    </Card>
                ) : (
                    <Stack gap="xl">
                        {/* Sessions to Accept (Pending) */}
                        <div>
                            <Title order={3} mb="md">Sesiones por Aceptar</Title>
                            {sessions.filter(s => {
                                const myPlayer = s.players.find(p => p.userId === user?.id);
                                return myPlayer?.status === 'PENDING';
                            }).length === 0 ? (
                                <Text c="dimmed">No tienes sesiones pendientes de aceptar.</Text>
                            ) : (
                                <Grid>
                                    {sessions
                                        .filter(s => {
                                            const myPlayer = s.players.find(p => p.userId === user?.id);
                                            return myPlayer?.status === 'PENDING';
                                        })
                                        .map((session) => (
                                            <SessionCard
                                                key={session.id}
                                                session={session}
                                                userId={user?.id}
                                                onAccept={handleAcceptSession}
                                                onReject={handleOpenRejectModal}
                                            />
                                        ))}
                                </Grid>
                            )}
                        </div>

                        {/* Accepted Sessions (Preliminary) */}
                        <div>
                            <Title order={3} mb="md">Sesiones Aceptadas (Esperando confirmación)</Title>
                            {sessions.filter(s => {
                                const myPlayer = s.players.find(p => p.userId === user?.id);
                                return myPlayer?.status === 'ACCEPTED' && s.status === 'PRELIMINARY';
                            }).length === 0 ? (
                                <Text c="dimmed">No tienes sesiones aceptadas pendientes de confirmación.</Text>
                            ) : (
                                <Grid>
                                    {sessions
                                        .filter(s => {
                                            const myPlayer = s.players.find(p => p.userId === user?.id);
                                            return myPlayer?.status === 'ACCEPTED' && s.status === 'PRELIMINARY';
                                        })
                                        .map((session) => (
                                            <SessionCard
                                                key={session.id}
                                                session={session}
                                                userId={user?.id}
                                                onAccept={handleAcceptSession}
                                                onReject={handleOpenRejectModal}
                                            />
                                        ))}
                                </Grid>
                            )}
                        </div>

                        {/* Confirmed Sessions */}
                        <div>
                            <Title order={3} mb="md">Sesiones Confirmadas</Title>
                            {sessions.filter(s => s.status === 'CONFIRMED').length === 0 ? (
                                <Text c="dimmed">No hay sesiones confirmadas.</Text>
                            ) : (
                                <Grid>
                                    {sessions
                                        .filter(s => s.status === 'CONFIRMED')
                                        .map((session) => (
                                            <SessionCard
                                                key={session.id}
                                                session={session}
                                                userId={user?.id}
                                                onAccept={handleAcceptSession}
                                                onReject={handleOpenRejectModal}
                                            />
                                        ))}
                                </Grid>
                            )}
                        </div>
                    </Stack>
                )}
            </Stack>

            <Modal opened={rejectModalOpened} onClose={closeRejectModal} title="Rechazar Sesión" centered>
                <Stack>
                    <Text>¿Por qué quieres rechazar esta sesión?</Text>
                    <Radio.Group value={rejectionReason} onChange={setRejectionReason}>
                        <Stack gap="xs">
                            <Radio value="NOT_AVAILABLE" label="No estoy disponible a esta hora (Eliminar disponibilidad)" />
                            <Radio value="DONT_WANT_GAME" label="No quiero jugar a este juego ahora (Mantener disponibilidad)" />
                        </Stack>
                    </Radio.Group>
                    <Group justify="flex-end" mt="md">
                        <Button variant="default" onClick={closeRejectModal}>Cancelar</Button>
                        <Button color="red" onClick={handleConfirmReject}>Confirmar Rechazo</Button>
                    </Group>
                </Stack>
            </Modal>
        </Container>
    );
}

function SessionCard({ session, userId, onAccept, onReject }: {
    session: Session;
    userId?: string;
    onAccept: (id: string) => void;
    onReject: (id: string) => void;
}) {
    const playerCount = session.players.length;
    const isParticipant = session.players.some(p => p.userId === userId && p.status === 'PENDING');

    const borderColor = session.status === 'CONFIRMED' ? 'green' : 'orange';

    return (
        <Grid.Col span={{ base: 12, md: 6, lg: 4 }}>
            <Card shadow="sm" padding="lg" radius="md" withBorder style={{ borderColor: borderColor, borderWidth: 2 }}>
                <Stack gap="sm">
                    <Group justify="space-between">
                        <Text fw={700} size="lg">
                            {session.game.title}
                        </Text>
                        <Group gap="xs">
                            {session.status === 'PRELIMINARY' && <Badge color="orange">Preliminar</Badge>}
                            {session.status === 'CONFIRMED' && <Badge color="green">Confirmada</Badge>}
                            {session.game.genre && (
                                <Badge variant="light">{session.game.genre}</Badge>
                            )}
                        </Group>
                    </Group>

                    <Group gap="xs">
                        <IconCalendar size={16} />
                        <Text size="sm">
                            {format(new Date(session.startTime), "PPP 'a las' HH:mm", {
                                locale: es,
                            })}
                        </Text>
                    </Group>

                    <Group gap="xs">
                        <IconUsers size={16} />
                        <Text size="sm">{playerCount} jugadores</Text>
                    </Group>

                    <Text size="xs" c="dimmed">
                        Score: {session.sessionScore.toFixed(1)}
                    </Text>

                    {isParticipant && (
                        <Group mt="md" grow>
                            <Button
                                color="green"
                                leftSection={<IconCheck size={16} />}
                                onClick={() => onAccept(session.id)}
                            >
                                Aceptar
                            </Button>
                            <Button
                                color="red"
                                variant="light"
                                leftSection={<IconX size={16} />}
                                onClick={() => onReject(session.id)}
                            >
                                Rechazar
                            </Button>
                        </Group>
                    )}
                </Stack>
            </Card>
        </Grid.Col>
    );
}
