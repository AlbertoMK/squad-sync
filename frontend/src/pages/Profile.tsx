import { Container, Title, Card, Stack, Text, Group, Avatar, TextInput, Button } from '@mantine/core';
import { useAuth } from '../contexts/AuthContext';
import { useState, useEffect } from 'react';
import { notifications } from '@mantine/notifications';

export default function Profile() {
    const { user, updateProfile } = useAuth();
    const [discordUsername, setDiscordUsername] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (user?.discordUsername) {
            setDiscordUsername(user.discordUsername);
        }
    }, [user]);

    const handleUpdate = async () => {
        setLoading(true);
        try {
            await updateProfile({ discordUsername });
            notifications.show({
                title: 'Perfil actualizado',
                message: 'Tu usuario de Discord ha sido guardado',
                color: 'green',
            });
        } catch (error) {
            notifications.show({
                title: 'Error',
                message: 'No se pudo actualizar el perfil',
                color: 'red',
            });
        } finally {
            setLoading(false);
        }
    };

    if (!user) return null;

    return (
        <Container size="sm">
            <Stack gap="lg">
                <Title order={2}>Mi Perfil</Title>

                <Card shadow="sm" padding="lg" radius="md" withBorder>
                    <Stack gap="md">
                        <Group>
                            <Avatar color={user.avatarColor} size="xl" radius="xl">
                                {user.username.charAt(0).toUpperCase()}
                            </Avatar>
                            <div>
                                <Text size="xl" fw={700}>
                                    {user.username}
                                </Text>
                                <Text size="sm" c="dimmed">
                                    {user.email}
                                </Text>
                            </div>
                        </Group>

                        <div>
                            <Text size="sm" c="dimmed" mb="xs">
                                Color de avatar
                            </Text>
                            <Group>
                                <div
                                    style={{
                                        width: 40,
                                        height: 40,
                                        borderRadius: '50%',
                                        backgroundColor: user.avatarColor,
                                        border: '2px solid var(--mantine-color-gray-6)',
                                    }}
                                />
                                <Text size="sm">{user.avatarColor}</Text>
                            </Group>
                        </div>

                        <TextInput
                            label="Usuario de Discord"
                            placeholder="usuario354"
                            description="Para notificarte cuando se armen partidas"
                            value={discordUsername}
                            onChange={(e) => setDiscordUsername(e.target.value)}
                        />

                        <Group justify="flex-end">
                            <Button onClick={handleUpdate} loading={loading}>
                                Guardar Cambios
                            </Button>
                        </Group>
                    </Stack>
                </Card>
            </Stack>
        </Container>
    );
}
