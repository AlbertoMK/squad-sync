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
import { IconRefresh, IconCalendar, IconUsers, IconCheck, IconX, IconClock, IconStar } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { matchmakingAPI, sessionsAPI, availabilityAPI } from '../lib/api';
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

    const [userSlots, setUserSlots] = useState<any[]>([]); // Add state for slots
    const [hasAvailabilityOverlap, setHasAvailabilityOverlap] = useState(true); // State for modal

    const loadSessions = async () => {
        try {
            const [sessionsRes, slotsRes] = await Promise.all([
                matchmakingAPI.getSessions(),
                availabilityAPI.getAll() // Fetch user slots
            ]);
            setSessions(sessionsRes.data);
            setUserSlots(slotsRes.data);
        } catch (error) {
            console.error('Error loading data:', error);
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

    const handleRejectSession = async (sessionId: string, reason: string = 'NOT_AVAILABLE') => {
        try {
            await sessionsAPI.reject(sessionId, reason);
            notifications.show({
                title: 'Sesión rechazada',
                message: reason === 'NOT_AVAILABLE'
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

    const handleOpenRejectModal = (sessionId: string) => {
        const session = sessions.find(s => s.id === sessionId);
        if (session) {
            const sessionStart = new Date(session.startTime).getTime();
            const sessionEnd = new Date(session.endTime).getTime();

            const overlap = userSlots.some(slot => {
                const slotStart = new Date(slot.startTime).getTime();
                const slotEnd = new Date(slot.endTime).getTime();
                return slotStart < sessionEnd && slotEnd > sessionStart;
            });

            if (!overlap) {
                // No availability to delete, so just reject/leave immediately
                handleRejectSession(sessionId, 'DONT_WANT_GAME');
                return;
            }

            setSelectedSessionId(sessionId);
            setHasAvailabilityOverlap(true);
            setRejectionReason('NOT_AVAILABLE');
            openRejectModal();
        }
    };

    const handleConfirmReject = () => {
        if (selectedSessionId) {
            handleRejectSession(selectedSessionId, rejectionReason);
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
                        {/* Row 1: Confirmed Sessions */}
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

                        {/* Row 2: Pending & Accepted Sessions */}
                        <Grid gutter="xl">
                            <Grid.Col span={{ base: 12, md: 6 }}>
                                <Title order={3} mb="md">Sesiones por Aceptar</Title>
                                {sessions.filter(s => {
                                    const myPlayer = s.players.find(p => p.userId === user?.id);
                                    return myPlayer?.status === 'PENDING' && s.status === 'PRELIMINARY';
                                }).length === 0 ? (
                                    <Text c="dimmed">No tienes sesiones pendientes de aceptar.</Text>
                                ) : (
                                    <Stack>
                                        {sessions
                                            .filter(s => {
                                                const myPlayer = s.players.find(p => p.userId === user?.id);
                                                return myPlayer?.status === 'PENDING' && s.status === 'PRELIMINARY';
                                            })
                                            .map((session) => (
                                                <SessionCard
                                                    key={session.id}
                                                    session={session}
                                                    userId={user?.id}
                                                    onAccept={handleAcceptSession}
                                                    onReject={handleOpenRejectModal}
                                                    fullWidth
                                                />
                                            ))}
                                    </Stack>
                                )}
                            </Grid.Col>

                            <Grid.Col span={{ base: 12, md: 6 }}>
                                <Title order={3} mb="md">Sesiones Aceptadas</Title>
                                {sessions.filter(s => {
                                    const myPlayer = s.players.find(p => p.userId === user?.id);
                                    return myPlayer?.status === 'ACCEPTED' && s.status === 'PRELIMINARY';
                                }).length === 0 ? (
                                    <Text c="dimmed">No tienes sesiones aceptadas pendientes.</Text>
                                ) : (
                                    <Stack>
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
                                                    fullWidth
                                                />
                                            ))}
                                    </Stack>
                                )}
                            </Grid.Col>
                        </Grid>

                        {/* Row 3: Other Sessions (Not Invited) */}
                        <div>
                            <Title order={3} mb="md">Otras Sesiones (No invitado)</Title>
                            {sessions.filter(s => {
                                const isPlayer = s.players.some(p => p.userId === user?.id);
                                return !isPlayer && s.status === 'PRELIMINARY';
                            }).length === 0 ? (
                                <Text c="dimmed">No hay otras sesiones disponibles.</Text>
                            ) : (
                                <Grid>
                                    {sessions
                                        .filter(s => {
                                            const isPlayer = s.players.some(p => p.userId === user?.id);
                                            return !isPlayer && s.status === 'PRELIMINARY';
                                        })
                                        .map((session) => (
                                            <SessionCard
                                                key={session.id}
                                                session={session}
                                                userId={user?.id}
                                                onAccept={handleAcceptSession}
                                                onReject={handleOpenRejectModal}
                                                readOnly
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
                            {hasAvailabilityOverlap && (
                                <Radio value="NOT_AVAILABLE" label="No estoy disponible a esta hora (Eliminar disponibilidad)" />
                            )}
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

function SessionCard({ session, userId, onAccept, onReject, readOnly = false, fullWidth = false }: {
    session: Session;
    userId?: string;
    onAccept: (id: string) => void;
    onReject: (id: string) => void;
    readOnly?: boolean;
    fullWidth?: boolean;
}) {
    const acceptedCount = session.players.filter(p => p.status === 'ACCEPTED').length;
    const userStatus = session.players.find(p => p.userId === userId)?.status;
    const isParticipant = userStatus === 'ACCEPTED';
    const isPending = userStatus === 'PENDING';

    const borderColor = session.status === 'CONFIRMED' ? 'green' : 'orange';

    // If fullWidth is true, we don't use Grid.Col, assuming parent handles layout or it's in a Stack
    const CardContent = (
        <Card shadow="sm" padding="lg" radius="md" withBorder style={{ borderColor: borderColor, borderWidth: 2, height: '100%' }}>
            <Stack gap="sm" justify="space-between" h="100%">
                <div>
                    <Group justify="space-between" mb="xs">
                        <Text fw={700} size="lg" lineClamp={1}>
                            {session.game.title}
                        </Text>
                        <Group gap="xs">
                            {session.status === 'PRELIMINARY' && <Badge color="orange">Preliminar</Badge>}
                            {session.status === 'CONFIRMED' && <Badge color="green">Confirmada</Badge>}
                        </Group>
                    </Group>

                    <Group gap="xs" mb={4}>
                        <IconCalendar size={16} />
                        <Text size="sm">
                            {format(new Date(session.startTime), "PPP 'a las' HH:mm", {
                                locale: es,
                            })}
                        </Text>
                    </Group>

                    <Group gap="xs" mb={4}>
                        <IconClock size={16} />
                        <Badge variant="gradient" gradient={{ from: 'indigo', to: 'cyan' }}>
                            {(() => {
                                const start = new Date(session.startTime);
                                const end = new Date(session.endTime);
                                const diffMilliseconds = end.getTime() - start.getTime();
                                const totalMinutes = Math.round(diffMilliseconds / (1000 * 60));
                                const hours = Math.floor(totalMinutes / 60);
                                const minutes = totalMinutes % 60;
                                return `${hours}h${minutes > 0 ? ` ${minutes}m` : ''}`;
                            })()}
                        </Badge>
                    </Group>

                    <Group gap="xs" mb={4}>
                        <IconStar size={16} />
                        <Text size="sm">Score: {session.sessionScore}</Text>
                    </Group>

                    <Group gap="xs">
                        <IconUsers size={16} />
                        <Text size="sm">
                            {session.status === 'CONFIRMED'
                                ? `${acceptedCount} confirmados`
                                : `${session.players.length} invitados`}
                        </Text>
                    </Group>
                </div>

                {!readOnly && (
                    <div>
                        {/* Actions for Confirmed Sessions */}
                        {session.status === 'CONFIRMED' && (
                            <Group mt="md" grow>
                                {isParticipant ? (
                                    <Button
                                        color="red"
                                        variant="light"
                                        leftSection={<IconX size={16} />}
                                        onClick={() => onReject(session.id)}
                                    >
                                        Desapuntarme
                                    </Button>
                                ) : (
                                    <Button
                                        color="green"
                                        leftSection={<IconCheck size={16} />}
                                        onClick={() => onAccept(session.id)}
                                    >
                                        Apuntarme
                                    </Button>
                                )}
                            </Group>
                        )}

                        {/* Actions for Preliminary Sessions */}
                        {session.status === 'PRELIMINARY' && isPending && (
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
                        {session.status === 'PRELIMINARY' && isParticipant && (
                            <Group mt="md" grow>
                                <Button
                                    color="red"
                                    variant="light"
                                    leftSection={<IconX size={16} />}
                                    onClick={() => onReject(session.id)}
                                >
                                    Cancelar asistencia
                                </Button>
                            </Group>
                        )}
                    </div>
                )}
                {readOnly && (
                    <Text size="xs" c="dimmed" mt="md" ta="center">
                        Añade disponibilidad a esta hora para ser invitado
                    </Text>
                )}
            </Stack>
        </Card>
    );

    if (fullWidth) {
        return CardContent;
    }

    return (
        <Grid.Col span={{ base: 12, md: 6, lg: 4 }}>
            {CardContent}
        </Grid.Col>
    );
}
