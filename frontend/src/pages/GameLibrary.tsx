import { useState, useEffect } from 'react';
import {
    Container,
    Title,
    Grid,
    Card,
    Text,
    Stack,
    Button,
    Modal,
    TextInput,
    NumberInput,
    Slider,
    Group,
    Badge,
    Loader,
    Center,
    ActionIcon,
} from '@mantine/core';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { gamesAPI, preferencesAPI } from '../lib/api';

interface Game {
    id: string;
    title: string;
    minPlayers: number;
    maxPlayers: number;
    genre?: string;
    coverImageUrl?: string;
}

interface Preference {
    id: string;
    weight: number;
    game: Game;
}

export default function GameLibrary() {
    const [games, setGames] = useState<Game[]>([]);
    const [preferences, setPreferences] = useState<Preference[]>([]);
    const [loading, setLoading] = useState(true);
    const [modalOpened, setModalOpened] = useState(false);
    const [newGame, setNewGame] = useState({
        title: '',
        minPlayers: 1,
        maxPlayers: 10,
        genre: '',
        coverImageUrl: '',
    });

    const loadData = async () => {
        try {
            const [gamesRes, prefsRes] = await Promise.all([
                gamesAPI.getAll(),
                preferencesAPI.getAll(),
            ]);
            setGames(gamesRes.data);
            setPreferences(prefsRes.data);
        } catch (error) {
            console.error('Error loading data:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleCreateGame = async () => {
        try {
            await gamesAPI.create(newGame);
            notifications.show({
                title: 'Juego añadido',
                message: `${newGame.title} ha sido añadido a la biblioteca`,
                color: 'green',
            });
            setModalOpened(false);
            setNewGame({ title: '', minPlayers: 1, maxPlayers: 10, genre: '', coverImageUrl: '' });
            await loadData();
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al crear juego',
                color: 'red',
            });
        }
    };

    const handleSetPreference = async (gameId: string, weight: number) => {
        try {
            await preferencesAPI.set({ gameId, weight });
            await loadData();
        } catch (error) {
            console.error('Error setting preference:', error);
        }
    };

    const getPreferenceWeight = (gameId: string): number => {
        const pref = preferences.find((p) => p.game.id === gameId);
        return pref ? pref.weight : 5;
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
                    <Title order={2}>Biblioteca de Juegos</Title>
                    <Button leftSection={<IconPlus size={16} />} onClick={() => setModalOpened(true)}>
                        Añadir Juego
                    </Button>
                </Group>

                <Grid>
                    {games.map((game) => {
                        const weight = getPreferenceWeight(game.id);

                        return (
                            <Grid.Col key={game.id} span={{ base: 12, md: 6, lg: 4 }}>
                                <Card shadow="sm" padding="lg" radius="md" withBorder>
                                    <Stack gap="sm">
                                        <Group justify="space-between">
                                            <Text fw={700} size="lg">
                                                {game.title}
                                            </Text>
                                            {game.genre && <Badge variant="light">{game.genre}</Badge>}
                                        </Group>

                                        <Text size="sm" c="dimmed">
                                            {game.minPlayers}-{game.maxPlayers} jugadores
                                        </Text>

                                        <div>
                                            <Text size="sm" mb="xs">
                                                Tu preferencia: {weight}/10
                                            </Text>
                                            <Slider
                                                value={weight}
                                                onChange={(value) => handleSetPreference(game.id, value)}
                                                min={1}
                                                max={10}
                                                step={1}
                                                marks={[
                                                    { value: 1, label: '1' },
                                                    { value: 5, label: '5' },
                                                    { value: 10, label: '10' },
                                                ]}
                                            />
                                        </div>
                                    </Stack>
                                </Card>
                            </Grid.Col>
                        );
                    })}
                </Grid>
            </Stack>

            <Modal
                opened={modalOpened}
                onClose={() => setModalOpened(false)}
                title="Añadir Nuevo Juego"
            >
                <Stack>
                    <TextInput
                        label="Título"
                        placeholder="Nombre del juego"
                        required
                        value={newGame.title}
                        onChange={(e) => setNewGame({ ...newGame, title: e.target.value })}
                    />

                    <TextInput
                        label="Género"
                        placeholder="Ej: FPS, RPG, Estrategia"
                        value={newGame.genre}
                        onChange={(e) => setNewGame({ ...newGame, genre: e.target.value })}
                    />

                    <NumberInput
                        label="Mínimo de jugadores"
                        min={1}
                        value={newGame.minPlayers}
                        onChange={(value) => setNewGame({ ...newGame, minPlayers: Number(value) })}
                    />

                    <NumberInput
                        label="Máximo de jugadores"
                        min={1}
                        value={newGame.maxPlayers}
                        onChange={(value) => setNewGame({ ...newGame, maxPlayers: Number(value) })}
                    />

                    <TextInput
                        label="URL de imagen (opcional)"
                        placeholder="https://..."
                        value={newGame.coverImageUrl}
                        onChange={(e) => setNewGame({ ...newGame, coverImageUrl: e.target.value })}
                    />

                    <Button onClick={handleCreateGame}>Añadir Juego</Button>
                </Stack>
            </Modal>
        </Container>
    );
}
