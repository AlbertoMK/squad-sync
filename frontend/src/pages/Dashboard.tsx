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
} from '@mantine/core';
import { IconRefresh, IconCalendar, IconUsers } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { matchmakingAPI } from '../lib/api';
import { format } from 'date-fns';
import { es } from 'date-fns/locale';

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
    playerIds: string;
}

export default function Dashboard() {
    const [sessions, setSessions] = useState<Session[]>([]);
    const [loading, setLoading] = useState(true);
    const [runningMatchmaking, setRunningMatchmaking] = useState(false);

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
                message: `Se crearon ${response.data.sessionsCreated} sesiones`,
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
                    <Button
                        leftSection={<IconRefresh size={16} />}
                        onClick={handleRunMatchmaking}
                        loading={runningMatchmaking}
                    >
                        Ejecutar Matchmaking
                    </Button>
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

                <div>
                    <Title order={3} mb="md">
                        Sesiones Propuestas
                    </Title>

                    {sessions.length === 0 ? (
                        <Card shadow="sm" padding="lg" radius="md" withBorder>
                            <Text c="dimmed" ta="center">
                                No hay sesiones propuestas aún. Ejecuta el matchmaking para encontrar partidas.
                            </Text>
                        </Card>
                    ) : (
                        <Grid>
                            {sessions.map((session) => {
                                const playerCount = JSON.parse(session.playerIds).length;

                                return (
                                    <Grid.Col key={session.id} span={{ base: 12, md: 6, lg: 4 }}>
                                        <Card shadow="sm" padding="lg" radius="md" withBorder>
                                            <Stack gap="sm">
                                                <Group justify="space-between">
                                                    <Text fw={700} size="lg">
                                                        {session.game.title}
                                                    </Text>
                                                    {session.game.genre && (
                                                        <Badge variant="light">{session.game.genre}</Badge>
                                                    )}
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
                                            </Stack>
                                        </Card>
                                    </Grid.Col>
                                );
                            })}
                        </Grid>
                    )}
                </div>
            </Stack>
        </Container>
    );
}
