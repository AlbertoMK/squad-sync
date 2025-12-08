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
    Image,
} from '@mantine/core';
import { IconPlus, IconTrash, IconEdit } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { gamesAPI, preferencesAPI } from '../lib/api';
import { useAuth } from '../contexts/AuthContext';

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
    const { user } = useAuth();
    const [games, setGames] = useState<Game[]>([]);
    const [preferences, setPreferences] = useState<Preference[]>([]);
    const [loading, setLoading] = useState(true);
    const [modalOpened, setModalOpened] = useState(false);
    const [isEditing, setIsEditing] = useState(false);
    const [editingGameId, setEditingGameId] = useState<string | null>(null);
    const [gameForm, setGameForm] = useState({
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

            const gamesData = gamesRes.data;
            const rawPrefs = prefsRes.data;

            // Map the raw preferences (which have gameId) to include the full game object
            const formattedPrefs = rawPrefs.map((p: any) => ({
                ...p,
                game: gamesData.find((g: Game) => g.id === p.gameId)
            })).filter((p: Preference) => p.game); // Filter out any preferences where game is not found

            setGames(gamesData);
            setPreferences(formattedPrefs);
        } catch (error) {
            console.error('Error loading data:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleOpenCreateModal = () => {
        setIsEditing(false);
        setEditingGameId(null);
        setGameForm({ title: '', minPlayers: 1, maxPlayers: 10, genre: '', coverImageUrl: '' });
        setModalOpened(true);
    };

    const handleOpenEditModal = (game: Game) => {
        setIsEditing(true);
        setEditingGameId(game.id);
        setGameForm({
            title: game.title,
            minPlayers: game.minPlayers,
            maxPlayers: game.maxPlayers,
            genre: game.genre || '',
            coverImageUrl: game.coverImageUrl || '',
        });
        setModalOpened(true);
    };

    const handleSubmitGame = async () => {
        try {
            if (isEditing && editingGameId) {
                await gamesAPI.update(editingGameId, gameForm);
                notifications.show({
                    title: 'Juego actualizado',
                    message: `${gameForm.title} ha sido actualizado`,
                    color: 'blue',
                });
            } else {
                await gamesAPI.create(gameForm);
                notifications.show({
                    title: 'Juego añadido',
                    message: `${gameForm.title} ha sido añadido a la biblioteca`,
                    color: 'green',
                });
            }
            setModalOpened(false);
            await loadData();
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || `Error al ${isEditing ? 'actualizar' : 'crear'} juego`,
                color: 'red',
            });
        }
    };

    const handleLocalPreferenceChange = (gameId: string, weight: number) => {
        setPreferences((prev) => {
            const existing = prev.find((p) => p.game?.id === gameId);
            if (existing) {
                return prev.map((p) => (p.game?.id === gameId ? { ...p, weight } : p));
            }
            // If it doesn't exist locally yet (default 5), we might need to handle it, 
            // but for now assuming we just update if it exists or wait for save.
            // Actually, if it's not in preferences, getPreferenceWeight returns 5.
            // We should probably add it to local state so it renders correctly.
            // However, we don't have the full Game object here easily to add a new Preference object 
            // without fetching or finding it in 'games'.
            // Simpler approach: Just update the UI state if possible, or rely on the fact 
            // that we will save it shortly. 
            // But to make it smooth, we need it in 'preferences'.

            // Let's find the game to add a temporary preference object
            const game = games.find(g => g.id === gameId);
            if (!game) return prev;

            return [...prev, { id: 'temp-' + gameId, weight, game }];
        });
    };

    const handleSavePreference = async (gameId: string, weight: number) => {
        try {
            const response = await preferencesAPI.set({ gameId, weight });
            // Update local state with the confirmed preference from server
            setPreferences((prev) => {
                const existing = prev.find((p) => p.game?.id === gameId);
                const game = existing?.game || games.find((g) => g.id === gameId);

                // Ensure we have the game object, as the API might not return the full nested object
                const newPref = {
                    ...response.data,
                    game: response.data.game || game
                };

                if (existing) {
                    return prev.map((p) => (p.game?.id === gameId ? newPref : p));
                }
                return [...prev, newPref];
            });
        } catch (error) {
            console.error('Error setting preference:', error);
            notifications.show({
                title: 'Error',
                message: 'No se pudo guardar la preferencia',
                color: 'red',
            });
        }
    };

    const handleDeleteGame = async (gameId: string) => {
        if (!window.confirm('¿Estás seguro de que quieres eliminar este juego?')) return;

        try {
            await gamesAPI.delete(gameId);
            notifications.show({
                title: 'Juego eliminado',
                message: 'El juego ha sido eliminado de la biblioteca',
                color: 'blue',
            });
            setGames((prev) => prev.filter((g) => g.id !== gameId));
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al eliminar juego',
                color: 'red',
            });
        }
    };

    const getPreferenceWeight = (gameId: string): number => {
        const pref = preferences.find((p) => p.game?.id === gameId);
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
                    {user?.role === 'ADMIN' && (
                        <Button leftSection={<IconPlus size={16} />} onClick={handleOpenCreateModal}>
                            Añadir Juego
                        </Button>
                    )}
                </Group>

                <Grid>
                    {games.map((game) => {
                        const weight = getPreferenceWeight(game.id);

                        return (
                            <Grid.Col key={game.id} span={{ base: 12, md: 6, lg: 4 }}>
                                <Card shadow="sm" padding="lg" radius="md" withBorder maw={320} mx="auto">
                                    {game.coverImageUrl && (
                                        <Card.Section>
                                            <Image
                                                src={game.coverImageUrl}
                                                height={160}
                                                alt={game.title}
                                            />
                                        </Card.Section>
                                    )}
                                    <Stack gap="sm" mt="md">
                                        <Group justify="space-between">
                                            <Text fw={700} size="lg">
                                                {game.title}
                                            </Text>
                                            <Group gap="xs">
                                                {game.genre && <Badge variant="light">{game.genre}</Badge>}
                                                {user?.role === 'ADMIN' && (
                                                    <>
                                                        <ActionIcon
                                                            color="blue"
                                                            variant="subtle"
                                                            onClick={() => handleOpenEditModal(game)}
                                                        >
                                                            <IconEdit size={16} />
                                                        </ActionIcon>
                                                        <ActionIcon
                                                            color="red"
                                                            variant="subtle"
                                                            onClick={() => handleDeleteGame(game.id)}
                                                        >
                                                            <IconTrash size={16} />
                                                        </ActionIcon>
                                                    </>
                                                )}
                                            </Group>
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
                                                onChange={(value) => handleLocalPreferenceChange(game.id, value)}
                                                onChangeEnd={(value) => handleSavePreference(game.id, value)}
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
                title={isEditing ? "Editar Juego" : "Añadir Nuevo Juego"}
            >
                <Stack>
                    <TextInput
                        label="Título"
                        placeholder="Nombre del juego"
                        required
                        value={gameForm.title}
                        onChange={(e) => setGameForm({ ...gameForm, title: e.target.value })}
                    />

                    <TextInput
                        label="Género"
                        placeholder="Ej: FPS, RPG, Estrategia"
                        value={gameForm.genre}
                        onChange={(e) => setGameForm({ ...gameForm, genre: e.target.value })}
                    />

                    <NumberInput
                        label="Mínimo de jugadores"
                        min={1}
                        value={gameForm.minPlayers}
                        onChange={(value) => setGameForm({ ...gameForm, minPlayers: Number(value) })}
                    />

                    <NumberInput
                        label="Máximo de jugadores"
                        min={1}
                        value={gameForm.maxPlayers}
                        onChange={(value) => setGameForm({ ...gameForm, maxPlayers: Number(value) })}
                    />

                    <TextInput
                        label="URL de imagen (opcional)"
                        placeholder="https://..."
                        value={gameForm.coverImageUrl}
                        onChange={(e) => setGameForm({ ...gameForm, coverImageUrl: e.target.value })}
                    />

                    <Button onClick={handleSubmitGame}>{isEditing ? "Guardar Cambios" : "Añadir Juego"}</Button>
                </Stack>
            </Modal>
        </Container>
    );
}
