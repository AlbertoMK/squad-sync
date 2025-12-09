import { Container, Title, Card, Stack, Text, Group, Avatar, TextInput, Button } from '@mantine/core';
import { useAuth } from '../contexts/AuthContext';
import { useState, useEffect } from 'react';
import { notifications } from '@mantine/notifications';

export default function Profile() {
    const { user, updateProfile } = useAuth();
    const [discordId, setDiscordId] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (user?.discordId) {
            setDiscordId(user.discordId);
        }
    }, [user]);

    const handleUpdate = async () => {
        setLoading(true);
        try {
            if (discordId && !/^\d+$/.test(discordId)) {
                notifications.show({
                    title: 'Error',
                    message: 'El ID de Discord debe contener solo números',
                    color: 'red',
                });
                setLoading(false);
                return;
            }
            await updateProfile({ discordId });
            notifications.show({
                title: 'Perfil actualizado',
                message: 'Tu ID de Discord ha sido guardado',
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
                            label="ID de Usuario de Discord"
                            placeholder="Ej: 123456789012345678"
                            description="Modo desarrollador > Click derecho en perfil > Copiar ID de usuario"
                            value={discordId}
                            onChange={(e) => setDiscordId(e.target.value)}
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
